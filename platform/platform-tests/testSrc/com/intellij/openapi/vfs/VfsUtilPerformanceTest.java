/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@SkipSlowTestLocally
public class VfsUtilPerformanceTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  @Test
  public void testFindChildByNamePerformance() throws IOException {
    File tempDir = myTempDir.newFolder();
    VirtualFile vDir = LocalFileSystem.getInstance().findFileByIoFile(tempDir);
    assertNotNull(vDir);
    assertTrue(vDir.isDirectory());

    new WriteCommandAction.Simple(null) {
      @Override
      protected void run() throws Throwable {
        for (int i = 0; i < 10000; i++) {
          String name = i + ".txt";
          vDir.createChildData(vDir, name);
        }
      }
    }.execute();

    VirtualFile theChild = vDir.findChild("5111.txt");
    assertNotNull(theChild);
    UIUtil.pump(); // wait for all event handlers to calm down

    System.out.println("Start searching...");
    PlatformTestUtil.startPerformanceTest("find child is slow", 1000, () -> {
      for (int i = 0; i < 1000000; i++) {
        VirtualFile child = vDir.findChild("5111.txt");
        assertEquals(theChild, child);
      }
    }).useLegacyScaling().assertTiming();

    new WriteCommandAction.Simple(null) {
      @Override
      protected void run() throws Throwable {
        for (VirtualFile file : vDir.getChildren()) {
          file.delete(this);
        }
      }
    }.execute().throwException();
  }

  @Test
  public void testFindRootPerformance() throws IOException {
    File tempJar = IoTestUtil.createTestJar(myTempDir.newFile("test.jar"));
    VirtualFile jar = LocalFileSystem.getInstance().findFileByIoFile(tempJar);
    assertNotNull(jar);

    JarFileSystem fs = JarFileSystem.getInstance();
    String path = jar.getPath() + "!/";
    NewVirtualFile root = ManagingFS.getInstance().findRoot(path, fs);
    PlatformTestUtil.startPerformanceTest(
      "find root is slow", 5000,
      () -> JobLauncher.getInstance().invokeConcurrentlyUnderProgress(
        Collections.nCopies(500, null), null, false, false,
        o -> {
          for (int i = 0; i < 20000; i++) {
            NewVirtualFile rootJar = ManagingFS.getInstance().findRoot(path, fs);
            assertNotNull(rootJar);
            assertSame(root, rootJar);
          }
          return true;
        })).useLegacyScaling().assertTiming();
  }

  @Test
  public void testGetParentPerformance() throws IOException {
    File tempDir = myTempDir.newFolder();
    VirtualFile vDir = LocalFileSystem.getInstance().findFileByIoFile(tempDir);
    assertNotNull(vDir);
    assertTrue(vDir.isDirectory());
    int depth = 10;
    new WriteCommandAction.Simple(null) {
      @Override
      protected void run() throws Throwable {
        VirtualFile dir = vDir;
        for (int i = 0; i < depth; i++) {
          dir = dir.createChildDirectory(this, "foo");
        }
        VirtualFile leafDir = dir;
        ThrowableRunnable checkPerformance = new ThrowableRunnable() {
          private VirtualFile findRoot(VirtualFile file) {
            while (true) {
              VirtualFile parent = file.getParent();
              if (parent == null) {
                return file;
              }
              file = parent;
            }
          }

          @Override
          public void run() throws Throwable {
            for (int i = 0; i < 5000000; i++) {
              checkRootsEqual();
            }
          }

          private void checkRootsEqual() {
            assertEquals(findRoot(vDir), findRoot(leafDir));
          }
        };
        int time = 1200;
        PlatformTestUtil.startPerformanceTest("getParent is slow before movement", time, checkPerformance).useLegacyScaling().assertTiming();
        VirtualFile dir1 = vDir.createChildDirectory(this, "dir1");
        VirtualFile dir2 = vDir.createChildDirectory(this, "dir2");
        for (int i = 0; i < 13; i++) {  /*13 is max length with THashMap capacity of 17, we get plenty collisions then*/
          dir1.createChildData(this, "a" + i + ".txt").move(this, dir2);
        }
        PlatformTestUtil.startPerformanceTest("getParent is slow after movement", time, checkPerformance).useLegacyScaling().assertTiming();
      }
    }.execute();
  }

  @Test
  public void testGetPathPerformance() throws IOException, InterruptedException {
    File dir = myTempDir.newFolder();

    String path = dir.getPath() + StringUtil.repeat("/xxx", 50) + "/fff.txt";
    File ioFile = new File(path);
    boolean b = ioFile.getParentFile().mkdirs();
    assertTrue(b);
    boolean c = ioFile.createNewFile();
    assertTrue(c);
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(ioFile.getPath().replace(File.separatorChar, '/'));
    assertNotNull(file);

    PlatformTestUtil.startPerformanceTest("VF.getPath() performance failed", 4000, () -> {
      for (int i = 0; i < 1000000; ++i) {
        file.getPath();
      }
    }).cpuBound().useLegacyScaling().assertTiming();
  }

  @Test
  public void testAsyncRefresh() throws Throwable {
    Ref<Throwable> ex = Ref.create();
    boolean success = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(
      Arrays.asList(new Object[8]), ProgressManager.getInstance().getProgressIndicator(), true,
      o -> {
        try {
          doAsyncRefreshTest();
        }
        catch (Throwable t) {
          ex.set(t);
        }
        return true;
      });

    if (!ex.isNull()) throw ex.get();
    if (!success) fail("!success");
  }

  private void doAsyncRefreshTest() throws Exception {
    int N = 1000;
    byte[] data = "xxx".getBytes(CharsetToolkit.UTF8_CHARSET);

    File temp = myTempDir.newFolder();
    LocalFileSystem fs = LocalFileSystem.getInstance();
    VirtualFile vTemp = fs.findFileByIoFile(temp);
    assertNotNull(vTemp);

    VirtualFile[] children = new VirtualFile[N];
    long[] timestamp = new long[N];

    for (int i = 0; i < N; i++) {
      File file = new File(temp, i + ".txt");
      FileUtil.writeToFile(file, data);
      VirtualFile child = fs.refreshAndFindFileByIoFile(file);
      assertNotNull(child);
      children[i] = child;
      timestamp[i] = file.lastModified();
    }

    vTemp.refresh(false, true);

    for (int i = 0; i < N; i++) {
      File file = new File(temp, i + ".txt");
      assertEquals(timestamp[i], file.lastModified());
      VirtualFile child = fs.findFileByIoFile(file);
      assertNotNull(child);
      IoTestUtil.assertTimestampsEqual(timestamp[i], child.getTimeStamp());
    }

    for (int i = 0; i < N; i++) {
      File file = new File(temp, i + ".txt");
      FileUtil.writeToFile(file, data);
      assertTrue(file.setLastModified(timestamp[i] - 2000));
      long modified = file.lastModified();
      assertTrue("File:" + file.getPath() + "; time:" + modified, timestamp[i] != modified);
      timestamp[i] = modified;
      IoTestUtil.assertTimestampsNotEqual(children[i].getTimeStamp(), modified);
    }

    CountDownLatch latch = new CountDownLatch(N);
    for (VirtualFile child : children) {
      child.refresh(true, true, latch::countDown);
      TimeoutUtil.sleep(10);
    }
    while (latch.getCount() > 0) {
      latch.await(100, TimeUnit.MILLISECONDS);
      UIUtil.pump();
    }

    for (int i = 0; i < N; i++) {
      VirtualFile child = children[i];
      IoTestUtil.assertTimestampsEqual(timestamp[i], child.getTimeStamp());
    }
  }
}