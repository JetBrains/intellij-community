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
package com.intellij.openapi.vfs.local;import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.win32.FileInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class IdeaWin32PerformanceTest {
  private IdeaWin32 myDriver;
  private long myJavaTotal, myIdeaTotal;

  @Before
  public void setUp() throws Exception {
    assumeTrue(SystemInfo.isWindows);
    myDriver = IdeaWin32.getInstance();
    myIdeaTotal = myJavaTotal = 0;
  }

  @Test
  public void list() throws Exception {
    String path = PathManager.getHomePath();
    doTest(new File(path));
    long gain = (myJavaTotal - myIdeaTotal) * 100 / myJavaTotal;
    String message = "home=" + path + " java.io=" + myJavaTotal / 1000 + "ms IdeaWin32=" + myIdeaTotal / 1000 + "ms gain=" + gain + "%";
    assertTrue(message, myIdeaTotal <= myJavaTotal);
    System.out.println(message);
  }

  private void doTest(File file) {
    final String path = file.getPath();

    assertNotNull(path, file.listFiles());
    assertNotNull(path, myDriver.listChildren(path));

    long t1 = System.nanoTime();
    final File[] children1 = file.listFiles();
    long t2 = System.nanoTime();
    final FileInfo[] children2 = myDriver.listChildren(path);
    long t3 = System.nanoTime();
    myJavaTotal += (t2 - t1) / 1000;
    myIdeaTotal += (t3 - t2) / 1000;

    assertNotNull(path, children1);
    assertNotNull(path, children2);
    assertEquals(path, children1.length, children2.length);

    for (File child : children1) {
      if (child.isDirectory()) {
        doTest(child);
      }
    }
  }
}
