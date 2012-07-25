/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import static org.junit.Assert.*;

public class IoTestUtil {
  private IoTestUtil() { }

  // todo[r.sh] use NIO2 API after migration to JDK 7
  public static File createTempLink(@NotNull final String target, @NotNull final String link) throws InterruptedException, IOException {
    final boolean isAbsolute = SystemInfo.isUnix && StringUtil.startsWithChar(link, '/') ||
                               SystemInfo.isWindows && link.matches("^[c-zC-Z]:[/\\\\].*$");
    final File linkFile = isAbsolute ? new File(link) : new File(FileUtil.getTempDirectory(), link);
    assertTrue(link, !linkFile.exists() || linkFile.delete());
    final File parentDir = linkFile.getParentFile();
    assertTrue("link=" + link + ", parent=" + parentDir, parentDir != null && (parentDir.isDirectory() || parentDir.mkdirs()));

    final ProcessBuilder command;
    if (SystemInfo.isWindows) {
      command = new File(target).isDirectory()
                ? new ProcessBuilder("cmd", "/C", "mklink", "/D", linkFile.getAbsolutePath(), target)
                : new ProcessBuilder("cmd", "/C", "mklink", linkFile.getAbsolutePath(), target);
    }
    else {
      command = new ProcessBuilder("ln", "-s", target, linkFile.getAbsolutePath());
    }
    final int res = runCommand(command);
    assertEquals(command.command().toString(), 0, res);

    final File targetFile = new File(target);
    final boolean shouldExist = targetFile.exists() || SystemInfo.isWindows && SystemInfo.JAVA_VERSION.startsWith("1.6");
    assertEquals("target=" + target + ", link=" + linkFile, shouldExist, linkFile.exists());
    return linkFile;
  }

  public static File createJunction(@NotNull final String target, @NotNull final String junction) throws InterruptedException, IOException {
    assertTrue(SystemInfo.isWindows);

    final File targetFile = new File(target);
    assertTrue(targetFile.getPath(), targetFile.isDirectory());

    final String exePath = getJunctionExePath();

    final boolean isAbsolute = junction.matches("^[c-zC-Z]:[/\\\\].*$");
    final File junctionFile = isAbsolute ? new File(junction) : new File(FileUtil.getTempDirectory(), junction);
    assertTrue(junction, !junctionFile.exists() || junctionFile.delete());
    final File parentDir = junctionFile.getParentFile();
    assertTrue("junction=" + junction + ", parent=" + parentDir, parentDir != null && (parentDir.isDirectory() || parentDir.mkdirs()));

    final ProcessBuilder command = new ProcessBuilder(exePath, junctionFile.getAbsolutePath(), target);
    final int res = runCommand(command);
    assertEquals(command.command().toString(), 0, res);

    assertTrue(junctionFile.getPath(), junctionFile.isDirectory());
    return junctionFile;
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
}
