/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.*;

public class IoTestUtil {
  private IoTestUtil() { }

  @NotNull
  public static File createSymLink(@NotNull String target, @NotNull String link) throws InterruptedException, IOException {
    return createSymLink(target, link, true);
  }

  @NotNull
  public static File createSymLink(@NotNull String target, @NotNull String link, boolean shouldExist) throws InterruptedException, IOException {
    assertTrue(SystemInfo.isWindows || SystemInfo.isUnix);

    final File targetFile = new File(FileUtil.toSystemDependentName(target));
    final File linkFile = getFullLinkPath(link);

    final ProcessBuilder command;
    if (SystemInfo.isWindows) {
      command = targetFile.isDirectory()
                ? new ProcessBuilder("cmd", "/C", "mklink", "/D", linkFile.getPath(), targetFile.getPath())
                : new ProcessBuilder("cmd", "/C", "mklink", linkFile.getPath(), targetFile.getPath());
    }
    else {
      command = new ProcessBuilder("ln", "-s", targetFile.getPath(), linkFile.getPath());
    }
    final int res = runCommand(command);
    assertEquals(command.command().toString(), 0, res);

    shouldExist |= SystemInfo.isWindows && SystemInfo.JAVA_VERSION.startsWith("1.6");
    assertEquals("target=" + target + ", link=" + linkFile, shouldExist, linkFile.exists());
    return linkFile;
  }

  @NotNull
  public static File createHardLink(@NotNull String target, @NotNull String link) throws InterruptedException, IOException {
    assertTrue(SystemInfo.isWindows || SystemInfo.isUnix);

    final File targetFile = new File(FileUtil.toSystemDependentName(target));
    final File linkFile = getFullLinkPath(link);

    final ProcessBuilder command;
    if (SystemInfo.isWindows) {
      command = new ProcessBuilder("fsutil", "hardlink", "create", linkFile.getPath(), targetFile.getPath());
    }
    else {
      command = new ProcessBuilder("ln", targetFile.getPath(), linkFile.getPath());
    }
    final int res = runCommand(command);
    assertEquals(command.command().toString(), 0, res);

    assertTrue("target=" + target + ", link=" + linkFile, linkFile.exists());
    return linkFile;
  }

  @NotNull
  public static File createJunction(@NotNull String target, @NotNull String junction) throws InterruptedException, IOException {
    assertTrue(SystemInfo.isWindows);

    final File targetFile = new File(FileUtil.toSystemDependentName(target));
    assertTrue(targetFile.getPath(), targetFile.isDirectory());

    final String exePath = getJunctionExePath();

    final File junctionFile = getFullLinkPath(junction);

    final ProcessBuilder command = new ProcessBuilder(exePath, junctionFile.getPath(), targetFile.getPath());
    final int res = runCommand(command);
    assertEquals(command.command().toString(), 0, res);

    assertTrue("target=" + target + ", link=" + junctionFile, junctionFile.isDirectory());
    return junctionFile;
  }

  @NotNull
  public static File createSubst(@NotNull String target) throws InterruptedException, IOException {
    assertTrue(SystemInfo.isWindows);

    final File targetFile = new File(FileUtil.toSystemDependentName(target));
    assertTrue(targetFile.getPath(), targetFile.isDirectory());

    final String substRoot = getFirstFreeDriveLetter() + ":";

    final ProcessBuilder command = new ProcessBuilder("subst", substRoot, targetFile.getPath());
    final int res = runCommand(command);
    assertEquals(command.command().toString(), 0, res);

    final File rootFile = new File(substRoot + "\\");
    assertTrue("target=" + target + ", subst=" + rootFile, rootFile.isDirectory());
    return rootFile;
  }

  public static void deleteSubst(@NotNull String substRoot) throws InterruptedException, IOException {
    runCommand(new ProcessBuilder("subst", StringUtil.trimEnd(substRoot,"\\"), "/d"));
  }

  private static char getFirstFreeDriveLetter() {
    final Set<Character> roots = ContainerUtil.map2Set(File.listRoots(), new Function<File, Character>() {
      @Override
      public Character fun(File root) {
        return root.getPath().toUpperCase(Locale.US).charAt(0);
      }
    });

    char drive = 0;
    for (char c = 'E'; c <= 'Z'; c++) {
      if (!roots.contains(c)) {
        drive = c;
        break;
      }
    }

    assertFalse("Occupied: " + roots.toString(), drive == 0);
    return drive;
  }

  private static File getFullLinkPath(String link) {
    File linkFile = new File(FileUtil.toSystemDependentName(link));
    if (!linkFile.isAbsolute()) {
      linkFile = new File(FileUtil.getTempDirectory(), link);
    }
    assertTrue(link, !linkFile.exists() || linkFile.delete());
    final File parentDir = linkFile.getParentFile();
    assertTrue("link=" + link + ", parent=" + parentDir, parentDir != null && (parentDir.isDirectory() || parentDir.mkdirs()));
    return linkFile;
  }

  private static String getJunctionExePath() throws IOException, InterruptedException {
    final URL url = IoTestUtil.class.getClassLoader().getResource("junction.exe");
    assertNotNull(url);

    final String path = url.getFile();
    final File util = new File(path);
    assertTrue(util.exists());

    final ProcessBuilder command = new ProcessBuilder(path, "/acceptEULA");
    final int res = runCommand(command);
    assertEquals(command.command().toString(), -1, res);

    return path;
  }

  private static int runCommand(final ProcessBuilder command) throws IOException, InterruptedException {
    final Process process = command.start();
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
          try {
            //noinspection StatementWithEmptyBody
            while (reader.readLine() != null);
          }
          finally {
            reader.close();
          }
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }).start();
    return process.waitFor();
  }

  public static void assertTimestampsEqual(final long expected, final long actual) {
    final long roundedExpected = (expected / 1000) * 1000;
    final long roundedActual = (actual / 1000) * 1000;
    assertEquals("expected: " + expected + ", actual: " + actual,
                 roundedExpected, roundedActual);
  }

  public static void assertTimestampsNotEqual(final long expected, final long actual) {
    final long roundedExpected = (expected / 1000) * 1000;
    final long roundedActual = (actual / 1000) * 1000;
    assertTrue("(un)expected: " + expected + ", actual: " + actual,
               roundedExpected != roundedActual);
  }

  @NotNull
  public static File createTestJar() throws IOException {
    File jarFile = FileUtil.createTempFile("test.", ".jar");
    writeEntry(jarFile, "entry.txt", "test");
    return jarFile;
  }

  public static void writeEntry(@NotNull File jarFile, @NotNull String name, @NotNull String content) throws IOException {
    JarOutputStream stream = new JarOutputStream(new FileOutputStream(jarFile));
    try {
      stream.putNextEntry(new JarEntry(name));
      stream.write(content.getBytes("UTF-8"));
      stream.closeEntry();
    }
    finally {
      stream.close();
    }
  }

  @NotNull
  public static File createTestDir(@NotNull String name) {
    return createTestDir(new File(FileUtil.getTempDirectory()), name);
  }

  @NotNull
  public static File createTestDir(@NotNull File parent, @NotNull String name) {
    File dir = new File(parent, name);
    assertTrue(dir.getPath(), dir.mkdirs());
    return dir;
  }

  @NotNull
  public static File createTestFile(@NotNull String name) throws IOException {
    return createTestFile(name, null);
  }

  @NotNull
  public static File createTestFile(@NotNull String name, @Nullable String content) throws IOException {
    return createTestFile(new File(FileUtil.getTempDirectory()), name, content);
  }

  @NotNull
  public static File createTestFile(@NotNull File parent, @NotNull String name) throws IOException {
    return createTestFile(parent, name, null);
  }

  @NotNull
  public static File createTestFile(@NotNull File parent, @NotNull String name, @Nullable String content) throws IOException {
    assertTrue(parent.getPath(), parent.isDirectory() || parent.mkdirs());
    File file = new File(parent, name);
    assertTrue(file.getPath(), file.createNewFile());
    if (content != null) {
      FileUtil.writeToFile(file, content);
    }
    return file;
  }

  public static void delete(File... files) {
    for (File file : files) {
      if (file != null) {
        FileUtil.delete(file);
      }
    }
  }

  public static void setHidden(@NotNull String path, boolean hidden) throws IOException, InterruptedException {
    assertTrue(SystemInfo.isWindows);

    ProcessBuilder command = new ProcessBuilder("attrib", hidden ? "+H" : "-H", path);
    int res = runCommand(command);
    assertEquals(command.command().toString(), 0, res);
  }
}
