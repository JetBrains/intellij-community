/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.concurrency.JobUtil;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformLangTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class VfsUtilTest extends PlatformLangTestCase {
  @Override
  protected void runBareRunnable(Runnable runnable) throws Throwable {
    runnable.run();
  }

  @Override
  protected void setUp() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          VfsUtilTest.super.setUp();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          VfsUtilTest.super.tearDown();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  public void testFindFileByUrl() throws Exception {
    VirtualFile file0;

    // Should not find in jre jars - it creates a lot of VirtualFileImpl!

//    file0 = VfsUtil.findFileByURL(ClassLoader.getSystemResource("java/lang/Object.class"));
//    assertNotNull(file0);
//    assertFalse(file0.isDirectory());
//
//    file0 = VfsUtil.findFileByURL(ClassLoader.getSystemResource("com/sun"));
//    assertNotNull(file0);
//    assertTrue(file0.isDirectory());

    File file1 = new File(PathManagerEx.getTestDataPath());
    file1 = new File(file1, "vfs");
    file1 = new File(file1, "findFileByUrl");
    file0 = VfsUtil.findFileByURL(file1.toURI().toURL());
    assertNotNull(file0);
    assertTrue(file0.isDirectory());
    final VirtualFile[] children = file0.getChildren();
    List<VirtualFile> list = new ArrayList<VirtualFile>();
    final VirtualFileFilter fileFilter = new VirtualFileFilter() {
      @Override
      public boolean accept(VirtualFile file) {
        return PlatformTestUtil.CVS_FILE_FILTER.accept(file) && !file.getName().endsWith(".new");
      }
    };
    for (VirtualFile child : children) {
      if (fileFilter.accept(child)) {
        list.add(child);
      }
    }
    assertEquals(2, list.size());     // "CVS" dir ignored

    File file2 = new File(file1, "test.zip");
    URL url2 = file2.toURI().toURL();
    url2 = new URL("jar", "", url2.toExternalForm() + "!/");
    url2 = new URL(url2, "com/intellij/installer");
    url2 = new URL(url2.toExternalForm());
    file0 = VfsUtil.findFileByURL(url2);
    assertNotNull(file0);
    assertTrue(file0.isDirectory());

    File file3 = new File(file1, "1.txt");
    file0 = VfsUtil.findFileByURL(file3.toURI().toURL());
    String content = VfsUtil.loadText(file0);
    assertNotNull(file0);
    assertFalse(file0.isDirectory());
    assertEquals(content, "test text");
  }

  public void testRelativePath() throws Exception {
    final File root = new File(PathManagerEx.getTestDataPath());
    final File testRoot = new File(new File(root, "vfs"), "relativePath");
    VirtualFile vTestRoot = LocalFileSystem.getInstance().findFileByIoFile(testRoot);
    assertNotNull(vTestRoot);
    assertTrue(vTestRoot.isDirectory());

    final File subDir = new File(testRoot, "subDir");
    final VirtualFile vSubDir = LocalFileSystem.getInstance().findFileByIoFile(subDir);
    assertNotNull(vSubDir);

    final File subSubDir = new File(subDir, "subSubDir");
    final VirtualFile vSubSubDir = LocalFileSystem.getInstance().findFileByIoFile(subSubDir);
    assertNotNull(vSubSubDir);

    assertEquals("subDir", VfsUtilCore.getRelativePath(vSubDir, vTestRoot, '/'));
    assertEquals("subDir/subSubDir", VfsUtilCore.getRelativePath(vSubSubDir, vTestRoot, '/'));
    assertEquals("", VfsUtilCore.getRelativePath(vTestRoot, vTestRoot, '/'));
  }

  public void testAsyncRefresh() throws Throwable {
    final Throwable[] ex = {null};
    JobUtil.invokeConcurrentlyUnderProgress(Arrays.asList(new Object[8]), ProgressManager.getInstance().getProgressIndicator(), false, new Processor<Object>() {
      @Override
      public boolean process(Object o) {
        try {
          doAsyncRefreshTest();
        }
        catch (Throwable t) {
          ex[0] = t;
        }
        return true;
      }
    });
    if (ex[0] != null) throw ex[0];
  }

  private void doAsyncRefreshTest() throws Exception {
    int N = 1000;
    File temp = new WriteAction<File>() {
      @Override
      protected void run(Result<File> result) throws Throwable {
        File res = createTempDirectory();
        result.setResult(res);
      }
    }.execute().getResultObject();
    LocalFileSystem fs = LocalFileSystem.getInstance();
    VirtualFile vTemp = fs.findFileByIoFile(temp);
    assert vTemp != null;
    VirtualFile[] children = new VirtualFile[N];

    long[] timestamp = new long[N];
    for (int i=0;i< N;i++) {
      File file = new File(temp, i + ".txt");
      FileUtil.writeToFile(file, "xxx".getBytes());
      VirtualFile child = fs.refreshAndFindFileByIoFile(file);
      assertNotNull(child);
      children[i] = child;
      timestamp[i] = file.lastModified();
    }

    vTemp.refresh(false, true);

    for (int i=0;i< N;i++) {
      File file = new File(temp, i + ".txt");
      assertEquals(timestamp[i], file.lastModified());
    }

    for (int i = 0; i < N; i++) {
      File file = new File(temp, i + ".txt");
      VirtualFile child = fs.findFileByIoFile(file);
      assertNotNull(child);

      long mod = child.getTimeStamp();
      assertEquals("File:" + child.getPath() + "; mod:" + new Date(mod) + "; io:" + new File(child.getPath()).lastModified(),
                          timestamp[i], mod);
    }

    Thread.sleep(2000);  // todo[r.sh] find a way to get timestamps with millisecond granularity on Linux ?
    for (int i=0;i< N;i++) {
      File file = new File(temp, i + ".txt");
      FileUtil.writeToFile(file, "xxx".getBytes());
      long modified = file.lastModified();
      assertTrue("File:" + file.getPath() + "; time:" + modified, timestamp[i] != modified);
      timestamp[i] = modified;
      assertTrue(children[i].getTimeStamp() != modified);
    }

    final CountDownLatch latch = new CountDownLatch(N);
    for (final VirtualFile child : children) {
      child.refresh(true, true, new Runnable() {
        @Override
        public void run() {
          latch.countDown();
        }
      });
    }
    while (true) {
      latch.await(100, TimeUnit.MILLISECONDS);
      if (SwingUtilities.isEventDispatchThread()) {
        UIUtil.dispatchAllInvocationEvents();
      }
      else {
        UIUtil.pump();
      }
      if (latch.getCount() == 0) break;
    }

    for (int i = 0; i < N; i++) {
      VirtualFile child = children[i];
      long mod = child.getTimeStamp();
      assertEquals(timestamp[i], mod);
    }
  }

  public void testFindChildWithTrailingSpace() throws IOException {
    File tempDir = new WriteAction<File>() {
      @Override
      protected void run(Result<File> result) throws Throwable {
        File res = createTempDirectory();
        result.setResult(res);
      }
    }.execute().getResultObject();
    VirtualFile vDir = LocalFileSystem.getInstance().findFileByIoFile(tempDir);
    assertNotNull(vDir);
    assertTrue(vDir.isDirectory());

    VirtualFile child = vDir.findChild(" ");
    assertNull(child);

    UsefulTestCase.assertEmpty(vDir.getChildren());
  }
}
