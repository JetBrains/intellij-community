// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.win32.FileInfo;
import com.intellij.openapi.util.io.win32.IdeaWin32;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

public class IdeaWin32PerformanceTest {
  private IdeaWin32 myDriver;
  private long myJavaTotal, myIdeaTotal;

  @Before
  public void setUp() {
    assumeTrue("windows only", SystemInfo.isWindows);
    myDriver = IdeaWin32.getInstance();
    myIdeaTotal = myJavaTotal = 0;
  }

  @Test
  public void list() {
    String path = PathManager.getHomePath();
    if (new File(path, "community").exists()) {
      path += "\\community";
    }

    doTest(new File(path));

    if (myIdeaTotal >= myJavaTotal) {
      long loss = (myIdeaTotal - myJavaTotal) * 100 / myJavaTotal;
      String message = "home=" + path + " java.io=" + myJavaTotal / 1000 + "ms IdeaWin32=" + myIdeaTotal / 1000 + "ms loss=" + loss + "%";
      assertThat(loss).describedAs(message).isLessThan(10);
      System.out.println(message);
    }
    else {
      long gain = (myJavaTotal - myIdeaTotal) * 100 / myJavaTotal;
      String message = "home=" + path + " java.io=" + myJavaTotal / 1000 + "ms IdeaWin32=" + myIdeaTotal / 1000 + "ms gain=" + gain + "%";
      System.out.println(message);
    }
  }

  private void doTest(File file) {
    String path = file.getPath();

    long t1 = System.nanoTime();
    File[] children1 = file.listFiles();
    long t2 = System.nanoTime();
    FileInfo[] children2 = myDriver.listChildren(path);
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