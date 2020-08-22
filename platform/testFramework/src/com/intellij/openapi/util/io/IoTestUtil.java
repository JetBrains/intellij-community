// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.intellij.Patches;
import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.io.StreamReadingCallable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.AssumptionViolatedException;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class IoTestUtil {
  @ReviseWhenPortedToJDK("13")
  private static final @Nullable Boolean symLinkMode =
    SystemInfo.isUnix ? Boolean.TRUE : SystemInfo.isWinVistaOrNewer ? canCreateSymlinks() : null;  // `TRUE` == NIO, `FALSE` == "mklink"
  public static final boolean isSymLinkCreationSupported = symLinkMode != null;

  private IoTestUtil() { }

  private static final String[] UNICODE_PARTS = {"Юникоде", "Úñíçødê"};

  @Nullable
  public static String getUnicodeName() {
    return filterParts(PathUtil::isValidFileName);
  }

  @Nullable
  public static String getUnicodeName(String forEncoding) {
    return filterParts(Charset.forName(forEncoding).newEncoder()::canEncode);
  }

  private static String filterParts(Predicate<? super String> predicate) {
    return StringUtil.nullize(Stream.of(UNICODE_PARTS).filter(predicate).collect(Collectors.joining("_")));
  }

  @NotNull
  public static File getTempDirectory() {
    File dir = new File(FileUtil.getTempDirectory());
    dir = expandWindowsPath(dir);
    return dir;
  }

  private static File expandWindowsPath(File file) {
    if (SystemInfo.isWindows && file.getPath().indexOf('~') > 0) {
      try {
        return file.getCanonicalFile();
      }
      catch (IOException ignored) { }
    }
    return file;
  }

  public static @NotNull File createSymLink(@NotNull String target, @NotNull String link) {
    return createSymLink(target, link, Boolean.TRUE);
  }

  public static @NotNull File createSymLink(@NotNull String target, @NotNull String link, boolean shouldExist) {
    return createSymLink(target, link, Boolean.valueOf(shouldExist));
  }

  /** A drop-in replacement for `Files#createSymbolicLink` needed until `Patches.JDK_BUG_ID_JDK_8218418` is resolved */
  public static @NotNull Path createSymbolicLink(@NotNull Path link, @NotNull Path target) throws IOException {
    try {
      return createSymLink(target.toString(), link.toString(), null).toPath();
    }
    catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  private static File createSymLink(String target, String link, @Nullable Boolean shouldExist) {
    File linkFile = getFullLinkPath(link), targetFile = new File(target);
    try {
      if (!Patches.JDK_BUG_ID_JDK_8218418) {
        Files.createSymbolicLink(linkFile.toPath(), targetFile.toPath());
      }
      else if (Files.isDirectory(targetFile.isAbsolute() ? targetFile.toPath() : linkFile.toPath().getParent().resolve(target))) {
        runCommand("cmd", "/C", "mklink", "/D", linkFile.getPath(), targetFile.getPath());
      }
      else {
        runCommand("cmd", "/C", "mklink", linkFile.getPath(), targetFile.getPath());
      }
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (shouldExist != null) {
      assertEquals("target=" + target + ", link=" + linkFile, shouldExist, linkFile.exists());
    }
    return linkFile;
  }

  public static void assumeSymLinkCreationIsSupported() throws AssumptionViolatedException {
    Assume.assumeTrue("Can't create symlinks on " + SystemInfo.OS_NAME, isSymLinkCreationSupported);
  }

  public static void assumeNioSymLinkCreationIsSupported() throws AssumptionViolatedException {
    Assume.assumeTrue("Can't create symlinks via NIO2 on " + SystemInfo.OS_NAME, symLinkMode == Boolean.TRUE);
  }

  public static void assumeWindows() throws AssumptionViolatedException {
    Assume.assumeTrue("Need Windows, can't run on " + SystemInfo.OS_NAME, SystemInfo.isWindows);
  }

  public static void assumeUnix() throws AssumptionViolatedException {
    Assume.assumeTrue("Need Unix, can't run on " + SystemInfo.OS_NAME, SystemInfo.isUnix);
  }

  @NotNull
  public static File createJunction(@NotNull String target, @NotNull String junction) {
    assertTrue(SystemInfo.isWindows);
    File targetFile = new File(target);
    assertTrue(targetFile.getPath(), targetFile.isDirectory());
    File junctionFile = getFullLinkPath(junction);
    runCommand("cmd", "/C", "mklink", "/J", junctionFile.getPath(), targetFile.getPath());
    assertTrue("target=" + targetFile + ", link=" + junctionFile, junctionFile.isDirectory());
    return junctionFile;
  }

  public static void deleteJunction(@NotNull String junction) {
    assertTrue(SystemInfo.isWindows);
    assertTrue(new File(junction).delete());
  }

  @NotNull
  public static File createSubst(@NotNull String target) {
    assertTrue(SystemInfo.isWindows);
    File targetFile = new File(target);
    assertTrue(targetFile.getPath(), targetFile.isDirectory());
    String substRoot = getFirstFreeDriveLetter() + ":";
    runCommand("subst", substRoot, targetFile.getPath());
    File rootFile = new File(substRoot + "\\");
    assertTrue("target=" + targetFile + ", subst=" + rootFile, rootFile.isDirectory());
    return rootFile;
  }

  public static void deleteSubst(@NotNull String substRoot) {
    runCommand("subst", StringUtil.trimEnd(substRoot, "\\"), "/d");
  }

  private static char getFirstFreeDriveLetter() {
    Set<Character> roots = StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), false)
      .map(root -> StringUtil.toUpperCase(root.toString()).charAt(0))
      .collect(Collectors.toSet());
    Logger.getInstance(IoTestUtil.class).debug("logical drives: " + roots);
    for (char c = 'E'; c <= 'Z'; c++) {
      if (!roots.contains(c)) {
        return c;
      }
    }
    throw new RuntimeException("No free roots");
  }

  private static File getFullLinkPath(String link) {
    File linkFile = new File(link);
    if (!linkFile.isAbsolute()) {
      linkFile = new File(getTempDirectory(), link);
    }
    assertTrue(link, !linkFile.exists() || linkFile.delete());
    File parentDir = linkFile.getParentFile();
    assertTrue("link=" + link + ", parent=" + parentDir, parentDir != null && (parentDir.isDirectory() || parentDir.mkdirs()));
    return linkFile;
  }

  private static void runCommand(String... command) {
    try {
      ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
      Process process = builder.start();
      Future<ByteArrayOutputStream> reader = ProcessIOExecutorService.INSTANCE.submit(new StreamReadingCallable(process));
      boolean finished = process.waitFor(30, TimeUnit.SECONDS);
      int ret = finished ? process.exitValue() : -1;
      ByteArrayOutputStream output = reader.get(30, TimeUnit.SECONDS);
      if (ret != 0) {
        throw new RuntimeException(builder.command() + "\nresult: " + ret + "\noutput:\n" + output.toString());
      }
    }
    catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  public static void assertTimestampsEqual(long expected, long actual) {
    long roundedExpected = (expected / 1000) * 1000;
    long roundedActual = (actual / 1000) * 1000;
    assertEquals("expected: " + expected + ", actual: " + actual,
                 roundedExpected, roundedActual);
  }

  public static void assertTimestampsNotEqual(long expected, long actual) {
    long roundedExpected = (expected / 1000) * 1000;
    long roundedActual = (actual / 1000) * 1000;
    assertTrue("(un)expected: " + expected + ", actual: " + actual,
               roundedExpected != roundedActual);
  }

  @NotNull
  public static File createTestJar(@NotNull File jarFile) {
    return createTestJar(jarFile, JarFile.MANIFEST_NAME, "");
  }

  @NotNull
  public static File createTestJar(@NotNull File jarFile, String @NotNull ... namesAndTexts) {
    try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(jarFile))) {
      for (int i = 0; i < namesAndTexts.length; i += 2) {
        stream.putNextEntry(new ZipEntry(namesAndTexts[i]));
        if (namesAndTexts[i + 1] != null) stream.write(namesAndTexts[i + 1].getBytes(StandardCharsets.UTF_8));
        stream.closeEntry();
      }
      return jarFile;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static File createTestJar(@NotNull File jarFile, @NotNull Collection<? extends Pair<String, byte[]>> namesAndContents) {
    try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(jarFile))) {
      for (Pair<String, byte[]> p : namesAndContents) {
        String name = p.first;
        byte[] content = p.second;
        stream.putNextEntry(new ZipEntry(name));
        stream.write(content);
        stream.closeEntry();
      }
      return jarFile;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static File createTestJar(@NotNull File jarFile, @NotNull File root) {
    try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(jarFile))) {
      FileUtil.visitFiles(root, file -> {
        if (file.isFile()) {
          String path = FileUtil.toSystemIndependentName(Objects.requireNonNull(FileUtil.getRelativePath(root, file)));
          try {
            stream.putNextEntry(new ZipEntry(path));
            try (InputStream is = new FileInputStream(file)) {
              FileUtil.copy(is, stream);
            }
            stream.closeEntry();
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
        return true;
      });
      return jarFile;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static File createTestDir(@NotNull String name) {
    return createTestDir(getTempDirectory(), name);
  }

  @NotNull
  public static File createTestDir(@NotNull File parent, @NotNull String name) {
    File dir = new File(parent, name);
    assertTrue(dir.getPath(), dir.mkdirs());
    return dir;
  }

  @NotNull
  public static File createTestFile(@NotNull String name) {
    return createTestFile(name, null);
  }

  @NotNull
  public static File createTestFile(@NotNull String name, @Nullable String content) {
    return createTestFile(getTempDirectory(), name, content);
  }

  @NotNull
  public static File createTestFile(@NotNull File parent, @NotNull String name) {
    return createTestFile(parent, name, null);
  }

  @NotNull
  public static File createTestFile(@NotNull File parent, @NotNull String name, @Nullable String content) {
    try {
      assertTrue(parent.getPath(), parent.isDirectory() || parent.mkdirs());
      File file = new File(parent, name);
      assertTrue(file.getPath(), file.createNewFile());
      if (content != null) {
        FileUtil.writeToFile(file, content);
      }
      return file;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void delete(File... files) {
    for (File file : files) {
      if (file != null) {
        FileUtil.delete(file);
      }
    }
  }

  public static void updateFile(@NotNull File file, String content) {
    try {
      FileUtil.writeToFile(file, content);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Boolean canCreateSymlinks() {
    try {
      Path target = Files.createTempFile("IOTestUtil_link_target.", ".txt");
      try {
        Path link = target.getParent().resolve("IOTestUtil_link");
        try {
          try {
            Files.createSymbolicLink(link, target.getFileName());
            return Boolean.TRUE;
          }
          catch (IOException e) {
            createSymbolicLink(link, target.getFileName());
            return Boolean.FALSE;
          }
        }
        finally {
          Files.deleteIfExists(link);
        }
      }
      finally {
        Files.delete(target);
      }
    }
    catch (Exception e) {
      //noinspection SSBasedInspection
      Logger.getInstance("#com.intellij.openapi.util.io.IoTestUtil").debug(e);
      return null;
    }
  }

  /* "C:\path" -> "\\127.0.0.1\C$\path" */
  public static @NotNull String toLocalUncPath(@NotNull String localPath) {
    return "\\\\127.0.0.1\\" + localPath.charAt(0) + '$' + localPath.substring(2);
  }
}
