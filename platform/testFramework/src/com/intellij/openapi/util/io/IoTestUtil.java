// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.io.SuperUserStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AssumptionViolatedException;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

public final class IoTestUtil {
  @ReviseWhenPortedToJDK("13")
  // `TRUE` == NIO, `FALSE` == "mklink", null == nothing works
  private static final @Nullable Boolean symLinkMode = SystemInfo.isUnix ? Boolean.TRUE : canCreateSymlinks();
  public static final boolean isSymLinkCreationSupported = symLinkMode != null;

  private IoTestUtil() { }

  @SuppressWarnings("SpellCheckingInspection")
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

  /** A drop-in replacement for `Files#createSymbolicLink` needed until migrating to Java 13+ */
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
      if (symLinkMode == Boolean.TRUE) {
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
    assumeTrue("Can't create symlinks on " + SystemInfo.getOsNameAndVersion(), isSymLinkCreationSupported);
  }

  public static void assumeNioSymLinkCreationIsSupported() throws AssumptionViolatedException {
    assumeTrue("Can't create symlinks via NIO2 on " + SystemInfo.getOsNameAndVersion(), symLinkMode == Boolean.TRUE);
  }

  public static void assumeWindows() throws AssumptionViolatedException {
    assumeTrue("Need Windows, can't run on " + SystemInfo.OS_NAME, SystemInfo.isWindows);
  }

  public static void assumeMacOS() throws AssumptionViolatedException {
    assumeTrue("Need macOS, can't run on " + SystemInfo.OS_NAME, SystemInfo.isMac);
  }

  public static void assumeLinux() throws AssumptionViolatedException {
    assumeTrue("Need Linux, can't run on " + SystemInfo.OS_NAME, SystemInfo.isLinux);
  }

  public static void assumeUnix() throws AssumptionViolatedException {
    assumeTrue("Need Unix, can't run on " + SystemInfo.OS_NAME, SystemInfo.isUnix);
  }

  public static void assumeCaseSensitiveFS() throws AssumptionViolatedException {
    assumeTrue("Assumed case sensitive FS but got " + SystemInfo.getOsNameAndVersion(), SystemInfo.isFileSystemCaseSensitive);
  }

  public static void assumeCaseInsensitiveFS() throws AssumptionViolatedException {
    assumeFalse("Assumed case insensitive FS but got " + SystemInfo.getOsNameAndVersion(), SystemInfo.isFileSystemCaseSensitive);
  }

  public static void assumeWslPresence() throws AssumptionViolatedException {
    assumeTrue("'wsl.exe' not found in %Path%", WSLDistribution.findWslExe() != null);
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

  private static @NotNull String runCommand(String @NotNull ... command) {
    try {
      GeneralCommandLine cmd = new GeneralCommandLine(command).withRedirectErrorStream(true);
      ProcessOutput output = ExecUtil.execAndGetOutput(cmd, 30_000);
      String out = output.getStdout().trim();
      if (output.getExitCode() != 0) {
        fail("failed: " + cmd + "\n" +
             "exit code: " + output.getExitCode() + "; output:\n" +
             out);
      }
      return out;
    }
    catch (ExecutionException e) {
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
            //noinspection RedundantSuppression,SSBasedInspection
            Logger.getInstance("#com.intellij.openapi.util.io.IoTestUtil").debug(e);
            runCommand("cmd", "/C", "mklink", link.toString(), target.getFileName().toString());
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
    catch (Throwable t) {
      //noinspection RedundantSuppression,SSBasedInspection
      Logger.getInstance("#com.intellij.openapi.util.io.IoTestUtil").debug(t);
      return null;
    }
  }

  /* "C:\path" -> "\\127.0.0.1\C$\path" */
  public static @NotNull String toLocalUncPath(@NotNull String localPath) {
    return "\\\\127.0.0.1\\" + localPath.charAt(0) + '$' + localPath.substring(2);
  }

  public static @NotNull List<@NotNull String> enumerateWslDistributions() {
    assertTrue(SystemInfo.isWin10OrNewer);
    try {
      GeneralCommandLine cmd = new GeneralCommandLine("wsl", "-l", "-q").withRedirectErrorStream(true).withCharset(StandardCharsets.UTF_16LE);
      ProcessOutput output = ExecUtil.execAndGetOutput(cmd, 30_000);
      if (output.getExitCode() == 0) {
        return output.getStdoutLines();
      }
      else {
        Logger.getInstance(IoTestUtil.class).debug(output.getExitCode() + " " + output.getStdout().trim());
      }
    }
    catch (Exception e) {
      Logger.getInstance(IoTestUtil.class).debug(e);
    }

    return Collections.emptyList();
  }

  public static boolean reanimateWslDistribution(@NotNull String name) {
    try {
      GeneralCommandLine cmd = new GeneralCommandLine("wsl", "-d", name, "-e", "pwd").withRedirectErrorStream(true);
      ProcessOutput output = ExecUtil.execAndGetOutput(cmd, 30_000);
      if (output.getExitCode() == 0) {
        return true;
      }
      else {
        Logger.getInstance(IoTestUtil.class).debug(output.getExitCode() + " " + output.getStdout().trim());
      }
    }
    catch (Exception e) {
      Logger.getInstance(IoTestUtil.class).debug(e);
    }

    return false;
  }

  public static void setCaseSensitivity(@NotNull File dir, boolean caseSensitive) throws IOException {
    assertTrue("'fsutil.exe' needs elevated privileges to work", SuperUserStatus.isSuperUser());
    String changeOut = runCommand("fsutil", "file", "setCaseSensitiveInfo", dir.getPath(), caseSensitive ? "enable" : "disable");
    String out = runCommand("fsutil", "file", "queryCaseSensitiveInfo", dir.getPath());
    if (!out.endsWith(caseSensitive ? "enabled." : "disabled.")) {
      throw new IOException("Can't setCaseSensitivity("+dir+", "+caseSensitive+"). 'fsutil.exe setCaseSensitiveInfo' output:"+changeOut+"; 'fsutil.exe getCaseSensitiveInfo' output:"+out);
    }
  }
}
