// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IoTestUtil {
  private IoTestUtil() { }

  @SuppressWarnings("SpellCheckingInspection") private static final String[] UNICODE_PARTS = {"Юникоде", "Úñíçødê"};

  @Nullable
  public static String getUnicodeName() {
    return filterParts(PathUtil::isValidFileName);
  }

  @Nullable
  public static String getUnicodeName(String forEncoding) {
    return filterParts(Charset.forName(forEncoding).newEncoder()::canEncode);
  }

  private static String filterParts(Predicate<String> predicate) {
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

  @NotNull
  public static File createSymLink(@NotNull String target, @NotNull String link) {
    return createSymLink(target, link, true);
  }

  @NotNull
  public static File createSymLink(@NotNull String target, @NotNull String link, boolean shouldExist) {
    File linkFile = getFullLinkPath(link);
    try {
      Files.createSymbolicLink(linkFile.toPath(), FileSystems.getDefault().getPath(target));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    assertEquals("target=" + target + ", link=" + linkFile, shouldExist, linkFile.exists());
    return linkFile;
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
    Set<Character> roots = ContainerUtil.map2Set(File.listRoots(), root -> root.getPath().toUpperCase(Locale.US).charAt(0));
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
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.redirectErrorStream(true);

      Process process = builder.start();
      StringBuilder output = new StringBuilder();
      Thread thread = new Thread(() -> {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
          String line;
          while ((line = reader.readLine()) != null) {
            output.append(line).append('\n');
          }
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }, "io test");
      thread.start();
      int ret = process.waitFor();
      thread.join();

      if (ret != 0) {
        throw new RuntimeException(builder.command() + "\nresult: " + ret + "\noutput:\n" + output);
      }
    }
    catch (IOException | InterruptedException e) {
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
  public static File createTestJar() {
    try {
      File jarFile = expandWindowsPath(FileUtil.createTempFile("test.", ".jar"));
      return createTestJar(jarFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static File createTestJar(File jarFile) {
    return createTestJar(jarFile, JarFile.MANIFEST_NAME, "");
  }

  @NotNull
  public static File createTestJar(@NotNull File jarFile, @NotNull String... namesAndTexts) {
    try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(jarFile))) {
      for (int i = 0; i < namesAndTexts.length; i += 2) {
        stream.putNextEntry(new ZipEntry(namesAndTexts[i]));
        if (namesAndTexts[i + 1] != null) stream.write(namesAndTexts[i + 1].getBytes(CharsetToolkit.UTF8_CHARSET));
        stream.closeEntry();
      }
      return jarFile;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static File createTestJar(@NotNull File jarFile, @NotNull Collection<Pair<String,byte[]>> namesAndContents) {
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
          String path = FileUtil.toSystemIndependentName(ObjectUtils.assertNotNull(FileUtil.getRelativePath(root, file)));
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
}