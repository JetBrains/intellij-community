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

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IoTestUtil {
  // todo[r.sh] use NIO2 API after migration to JDK 7
  public static File createTempLink(final String target, final String link) throws InterruptedException, IOException {
    final boolean isAbsolute = SystemInfo.isUnix && StringUtil.startsWithChar(link, '/') ||
                               SystemInfo.isWindows && link.matches("^[c-zC-Z]:[/\\\\].*$");
    final File linkFile = isAbsolute ? new File(link) : new File(FileUtil.getTempDirectory(), link);
    assertTrue(link, !linkFile.exists() || linkFile.delete());
    final File parentDir = linkFile.getParentFile();
    assertTrue("link=" + link + ", parent=" + parentDir, parentDir != null && (parentDir.isDirectory() || parentDir.mkdirs()));

    final ProcessBuilder commandLine;
    if (SystemInfo.isWindows) {
      commandLine = new File(target).isDirectory()
                    ? new ProcessBuilder("cmd", "/C", "mklink", "/D", linkFile.getAbsolutePath(), target)
                    : new ProcessBuilder("cmd", "/C", "mklink", linkFile.getAbsolutePath(), target);
    }
    else {
      commandLine = new ProcessBuilder("ln", "-s", target, linkFile.getAbsolutePath());
    }
    final int res = commandLine.start().waitFor();
    assertEquals(commandLine.command().toString(), 0, res);

    final File targetFile = new File(target);
    final boolean shouldExist = targetFile.exists() || SystemInfo.isWindows && SystemInfo.JAVA_VERSION.startsWith("1.6");
    assertEquals("target=" + target + ", link=" + linkFile, shouldExist, linkFile.exists());
    return linkFile;
  }
}
