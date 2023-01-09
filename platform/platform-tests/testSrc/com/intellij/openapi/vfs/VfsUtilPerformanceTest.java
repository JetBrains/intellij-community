// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.concurrency.JobLauncher;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.idea.HardwareAgentRequired;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.FrequentEventDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntSortedSets;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.*;

@RunFirst
@SkipSlowTestLocally
@HardwareAgentRequired
public class VfsUtilPerformanceTest {
  @Rule public ApplicationRule appRule = new ApplicationRule();
  @Rule public TempDirectory tempDir = new TempDirectory();
  @Rule public DisposableRule testDisposable = new DisposableRule();

  @BeforeClass
  public static void setupInStressTestsFlag() {
    ApplicationManagerEx.setInStressTest(true);
  }

  @AfterClass
  public static void clearInStressTestsFlag() {
    ApplicationManagerEx.setInStressTest(false);
  }

  @Test
  public void testFindChildByNamePerformance() throws IOException {
    File tempDir = this.tempDir.newDirectory();
    VirtualFile vDir = LocalFileSystem.getInstance().findFileByIoFile(tempDir);
    assertNotNull(vDir);
    assertTrue(vDir.isDirectory());

    WriteCommandAction.writeCommandAction(null).run(() -> {
      for (int i = 0; i < 10_000; i++) {
        String name = i + ".txt";
        vDir.createChildData(vDir, name);
      }
    });

    VirtualFile theChild = vDir.findChild("5111.txt");
    assertNotNull(theChild);
    UIUtil.pump(); // wait for all event handlers to calm down

    Logger.getInstance(VfsUtilPerformanceTest.class).debug("Start searching...");
    PlatformTestUtil.startPerformanceTest("finding child", 1500, () -> {
      for (int i = 0; i < 1_000_000; i++) {
        VirtualFile child = vDir.findChild("5111.txt");
        assertEquals(theChild, child);
      }
    }).assertTiming();

    WriteCommandAction.writeCommandAction(null).run(() -> {
      for (VirtualFile file : vDir.getChildren()) {
        file.delete(this);
      }
    });
  }

  @Test
  public void testFindRootPerformance() {
    File tempJar = IoTestUtil.createTestJar(tempDir.newFile("test.jar"));
    VirtualFile jar = LocalFileSystem.getInstance().findFileByIoFile(tempJar);
    assertNotNull(jar);

    JarFileSystem fs = JarFileSystem.getInstance();
    String path = jar.getPath() + "!/";
    ManagingFS managingFS = ManagingFS.getInstance();
    NewVirtualFile root = managingFS.findRoot(path, fs);
    PlatformTestUtil.startPerformanceTest("finding root", 20_000,
                                          () -> JobLauncher.getInstance().invokeConcurrentlyUnderProgress(
                                            Collections.nCopies(500, null), null,
                                            __ -> {
                                              for (int i = 0; i < 100_000; i++) {
                                                NewVirtualFile rootJar = managingFS.findRoot(path, fs);
                                                assertNotNull(rootJar);
                                                assertSame(root, rootJar);
                                              }
                                              return true;
                                            })).usesAllCPUCores().assertTiming();
  }

  @Test
  public void testGetParentPerformance() throws IOException {
    VirtualFile root = tempDir.getVirtualFileRoot();
    int depth = 10;
    int N = 5_000_000;
    int time = 1000;

    WriteCommandAction.writeCommandAction(null).run(() -> {
      VirtualFile dir = root;
      for (int i = 0; i < depth; i++) {
        dir = dir.createChildDirectory(this, "foo");
      }
      VirtualFile leafDir = dir;
      ThrowableRunnable<RuntimeException> checkPerformance = new ThrowableRunnable<>() {
        @Override
        public void run() {
          for (int i = 0; i < N; i++) checkRootReached();
        }

        private void checkRootReached() {
          assertTrue(findRoot(leafDir, root));
        }

        private static boolean findRoot(VirtualFile file, VirtualFile root) {
          while (true) {
            VirtualFile parent = file.getParent();
            if (parent == null) return false;
            if (root.equals(parent)) return true;
            file = parent;
          }
        }
      };

      PlatformTestUtil.startPerformanceTest("getParent before movement", time, checkPerformance).assertTiming();

      VirtualFile dir1 = root.createChildDirectory(this, "dir1");
      VirtualFile dir2 = root.createChildDirectory(this, "dir2");
      for (int i = 0; i < 13; i++) {  // 13 is max length with THashMap capacity of 17, we get plenty of collisions then
        dir1.createChildData(this, "a" + i + ".txt").move(this, dir2);
      }

      PlatformTestUtil.startPerformanceTest("getParent after movement", time, checkPerformance).assertTiming();
    });
  }

  @Test
  public void testGetPathPerformance() throws Exception {
    LightTempDirTestFixtureImpl fixture = new LightTempDirTestFixtureImpl();
    fixture.setUp();
    Disposer.register(testDisposable.getDisposable(), () -> EdtTestUtil.runInEdtAndWait(() -> {
      try {
        fixture.tearDown();
      }
      catch (Exception e) {
        ExceptionUtil.rethrowAllAsUnchecked(e);
      }
    }));

    EdtTestUtil.runInEdtAndWait(() -> {
      String path = "unitTest_testGetPathPerformance_6542623412414351229/" +
                    "junit6921058097194294088/" +
                    StringUtil.repeat("xxx/", 50) +
                    "fff.txt";
      VirtualFile file = fixture.findOrCreateDir(path);

      PlatformTestUtil.startPerformanceTest("VF.getPath()", 10_000, () -> {
        for (int i = 0; i < 1_000_000; ++i) {
          file.getPath();
        }
      }).assertTiming();
    });
  }

  @Test
  public void testAsyncRefresh() throws Throwable {
    var ex = new AtomicReference<Throwable>();
    var tasks = Collections.nCopies(JobSchedulerImpl.getJobPoolParallelism(), null);
    var success = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(tasks, null, __ -> {
      try {
        doAsyncRefreshTest();
      }
      catch (Throwable t) {
        ex.set(t);
      }
      return true;
    });

    if (ex.get() != null) throw ex.get();
    assertTrue(success);
  }

  private void doAsyncRefreshTest() throws Exception {
    var N = 1_000;
    var vFiles = new VirtualFile[N];
    var modStamps = new long[N];
    var temp = tempDir.newDirectoryPath();
    var fs = LocalFileSystem.getInstance();

    for (int i = 0; i < N; i++) {
      var file = Files.writeString(temp.resolve(i + ".txt"), "xxx");
      vFiles[i] = requireNonNull(fs.refreshAndFindFileByNioFile(file));
      modStamps[i] = Files.getLastModifiedTime(file).toMillis();
    }

    vFiles[0].getParent().refresh(false, true);

    for (int i = 0; i < N; i++) {
      var file = temp.resolve(i + ".txt");
      assertEquals(modStamps[i], Files.getLastModifiedTime(file).toMillis());
      var vFile = requireNonNull(fs.refreshAndFindFileByNioFile(file));
      assertEquals(modStamps[i], vFile.getTimeStamp());
    }

    for (int i = 0; i < N; i++) {
      var file = temp.resolve(i + ".txt");
      Files.setLastModifiedTime(file, FileTime.fromMillis(modStamps[i] - 2_000));
      var newModStamp = Files.getLastModifiedTime(file).toMillis();
      assertNotEquals(modStamps[i], newModStamp);
      modStamps[i] = newModStamp;
      assertNotEquals(vFiles[i].getTimeStamp(), newModStamp);
    }

    var latch = new CountDownLatch(N);
    var refreshEngaged = Disposer.newDisposable();
    try {
      FrequentEventDetector.disableUntil(refreshEngaged);
      for (VirtualFile vFile : vFiles) {
        vFile.refresh(true, true, latch::countDown);
      }
    }
    finally {
      Disposer.dispose(refreshEngaged);
    }
    assertTrue(latch.await(2, TimeUnit.MINUTES));

    for (int i = 0; i < N; i++) {
      assertEquals(modStamps[i], vFiles[i].getTimeStamp());
    }
  }

  @Test
  public void PersistentFS_performance_ofManyFilesCreateDelete() {
    //RC: adding .warmupIterations(1-2) reduce execution time 5-10x! Probably,
    //    because of JITing -- if that is true, then times after warm up are
    //    better represent real-life performance?
    int N = 30_000;
    List<VFileEvent> events = new ArrayList<>(N);
    VirtualDirectoryImpl temp = createTempFsDirectory();

    EdtTestUtil.runInEdtAndWait(() -> {
      PlatformTestUtil.startPerformanceTest("many files creations", 3_000, () -> {
        assertEquals(N, events.size());
        processEvents(events);
        assertEquals(N, temp.getCachedChildren().size());
      })
      .setup(() -> {
        eventsForDeleting(events, temp);
        if (!events.isEmpty()) {
          processEvents(events);
        }
        eventsForCreating(events, N, temp);
        assertEquals(N, TempFileSystem.getInstance().list(temp).length); // do not call getChildren which caches everything
      })
      .assertTiming();

      PlatformTestUtil.startPerformanceTest("many files deletions", 3_300, () -> {
        assertEquals(N, events.size());
        processEvents(events);
        assertEquals(0, temp.getCachedChildren().size());
      })
      .setup(() -> {
        if (temp.getCachedChildren().size() != N) {
          eventsForDeleting(events, temp);
          if (!events.isEmpty()) {
            processEvents(events);
          }
          eventsForCreating(events, N, temp);
          processEvents(events);
        }
        eventsForDeleting(events, temp);
        assertEquals(N, TempFileSystem.getInstance().list(temp).length); // do not call getChildren which caches everything
      })
      .assertTiming();
      }
    );
  }

  private VirtualDirectoryImpl createTempFsDirectory() {
    VirtualFile root = TempFileSystem.getInstance().findFileByPath("/");
    VirtualDirectoryImpl temp = (VirtualDirectoryImpl)VfsTestUtil.createDir(root, "temp");
    Disposer.register(testDisposable.getDisposable(), () -> VfsTestUtil.deleteFile(temp));
    return temp;
  }

  private static void processEvents(List<VFileEvent> events) {
    WriteCommandAction.runWriteCommandAction(null, () -> RefreshQueue.getInstance().processEvents(false, events));
  }

  private void eventsForCreating(List<VFileEvent> events, int N, VirtualDirectoryImpl temp) throws IOException {
    events.clear();
    TempFileSystem fs = TempFileSystem.getInstance();
    for (int i = 0; i < N; i++) {
      String childName = i + ".txt";
      fs.createIfNotExists(temp, childName);
      events.add(new VFileCreateEvent(this, temp, childName, false, null, null, false, null));
    }
    List<CharSequence> names = ContainerUtil.map(events, e -> ((VFileCreateEvent)e).getChildName());
    temp.removeChildren(IntSortedSets.EMPTY_SET, names);
  }

  private void eventsForDeleting(List<VFileEvent> events, VirtualDirectoryImpl temp) {
    events.clear();
    temp.getCachedChildren().stream()
      .map(v->new VFileDeleteEvent(this, v, false))
      .forEach(events::add);
  }
}
