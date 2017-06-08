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
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.VfsData;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

@JITSensitive
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
    PlatformTestUtil.startPerformanceTest("finding child", 1000, () -> {
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
      "finding root", 5000,
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
        PlatformTestUtil.startPerformanceTest("getParent before movement", time, checkPerformance).useLegacyScaling().assertTiming();
        VirtualFile dir1 = vDir.createChildDirectory(this, "dir1");
        VirtualFile dir2 = vDir.createChildDirectory(this, "dir2");
        for (int i = 0; i < 13; i++) {  /*13 is max length with THashMap capacity of 17, we get plenty collisions then*/
          dir1.createChildData(this, "a" + i + ".txt").move(this, dir2);
        }
        PlatformTestUtil.startPerformanceTest("getParent after movement", time, checkPerformance).useLegacyScaling().assertTiming();
      }
    }.execute();
  }

  @Test
  public void testGetPathPerformance() throws Exception {
    LightTempDirTestFixtureImpl fixture = new LightTempDirTestFixtureImpl();
    fixture.setUp();
    Disposer.register(getTestRootDisposable(), () -> {
      try {
        fixture.tearDown();
      }
      catch (Exception e) {
        ExceptionUtil.rethrowAllAsUnchecked(e);
      }
    });

    EdtTestUtil.runInEdtAndWait(() -> {
      String path = "unitTest_testGetPathPerformance_6542623412414351229/" +
                    "junit6921058097194294088/" +
                    StringUtil.repeat("xxx/", 50) +
                    "fff.txt";
      VirtualFile file = fixture.findOrCreateDir(path);

      PlatformTestUtil.startPerformanceTest("VF.getPath()", 4000, () -> {
        for (int i = 0; i < 1000000; ++i) {
          file.getPath();
        }
      }).useLegacyScaling().assertTiming();
    });
  }

  @Test
  public void testAsyncRefresh() throws Throwable {
    Ref<Throwable> ex = Ref.create();
    boolean success = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(
      Arrays.asList(new Object[JobSchedulerImpl.CORES_COUNT]), ProgressManager.getInstance().getProgressIndicator(), false,
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

  @Test
  public void addingManyChildrenToTheSameDirectoryMustNotBeQuadratic() throws Exception {
    int N = 1_000_000;
    // measures create N children inside one directory.
    // to avoid slow local file system try to use MyFakeVirtualFile which doesn't actually query disk
    // then call PersistentFS.getInstance().processEvents(events); directly instead of agonizingly slow refresh.
    ApplicationInfoImpl.setInStressTest(true); //wtf wrong with you fixtures?
    Disposer.register(getTestRootDisposable(), ()-> ApplicationInfoImpl.setInStressTest(false));
    List<VirtualFile> toDelete = new ArrayList<>();
    try {
      UIUtil.invokeAndWaitIfNeeded((Runnable)()-> PlatformTestUtil.startPerformanceTest("adding many children", 15000, () -> {
        VirtualFile validVTemp = new MyFakeDirectory("vtemp");
        toDelete.add(validVTemp);
        List<VFileEvent> events = IntStream.range(0, N)
          .mapToObj(i -> new VFileCreateEvent(this, validVTemp, i + ".txt", false, false))
          .collect(Collectors.toList());

        WriteCommandAction.runWriteCommandAction(null, () -> {
          PersistentFS.getInstance().processEvents(events);
        });

        assertEquals(N, validVTemp.getChildren().length);
      }).assertTiming());
    }
    finally {
      toDelete.forEach(VfsTestUtil::deleteFile);
    }
  }

  @Test
  public void deleteManyChildrenFromTheSameDirectoryMustNotBeQuadratic() throws IOException {
    int N = 1_000_000;
    // measures delete N children inside one directory.
    // to avoid slow local file system try to use MyFakeVirtualFile which doesn't actually query disk
    // then call PersistentFS.getInstance().processEvents(events); directly instead of agonizingly slow refresh.
    ApplicationInfoImpl.setInStressTest(true); //wtf wrong with you fixtures?
    //TranslatingCompilerFilesMonitor.getInstance().disable(getTestRootDisposable());
    Disposer.register(getTestRootDisposable(), ()-> ApplicationInfoImpl.setInStressTest(false));
    List<VirtualFile> toDelete = new ArrayList<>();
    try {
      UIUtil.invokeAndWaitIfNeeded((Runnable)()->{
        final VirtualDirectoryImpl[] validVTemp = new VirtualDirectoryImpl[1];
        List<VFileEvent> deleteEvents = new ArrayList<>();
        PlatformTestUtil.startPerformanceTest("deleting many children", 30000, () -> {
          WriteCommandAction.runWriteCommandAction(null, () -> {
            PersistentFS.getInstance().processEvents(deleteEvents);
          });

          assertEquals(0, validVTemp[0].getChildren().length);
        }).setup(()-> {
          // prepare fake dir with N fake children
          ((FileBasedIndexImpl) FileBasedIndex.getInstance()).cleanupForNextTest();
          GCUtil.tryForceGC();
          
          validVTemp[0] = new MyFakeDirectory("vtemp");
          validVTemp[0].getChildren();
          toDelete.add(validVTemp[0]);

          List<VFileEvent> createEvents = IntStream.range(0, N)
            .mapToObj(i -> new VFileCreateEvent(this, validVTemp[0], i + ".txt", false, false))
            .collect(Collectors.toList());

          WriteCommandAction.runWriteCommandAction(null, () -> {
            PersistentFS.getInstance().processEvents(createEvents);
          });
          assertEquals(N, validVTemp[0].getChildren().length);

          deleteEvents.clear();
          deleteEvents.addAll(Arrays.stream(validVTemp[0].getChildren())
            .map(v -> new VFileDeleteEvent(this, new MyFakeFile(v.getName(), validVTemp[0]), false))
            .collect(Collectors.toList()));

        }).assertTiming();
      });
    }
    finally {
      toDelete.forEach(VfsTestUtil::deleteFile);
    }
  }

  private static class MyFakeFile extends VirtualFileImpl {
    private static final VfsData.Segment SEGMENT = new VfsData.Segment();
    private final String name;

    MyFakeFile(String name, VirtualDirectoryImpl parent) {
      super(FSRecords.createRecord(), SEGMENT, parent);
      this.name = name;
    }

    @NotNull
    @Override
    public CharSequence getNameSequence() {
      return name;
    }

    @NotNull
    @Override
    public String getUrl() {
      return getName();
    }

    @NotNull
    @Override
    public String getPath() {
      return getName();
    }

    UserDataHolder data;
    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      if (data == null) data = new UserDataHolderBase();
      data.putUserData(key, value);
    }

    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      try {
        return data.getUserData(key);
      }
      finally {
        data.putUserData(key, null);
        if (((UserDataHolderBase)data).isUserDataEmpty()) data = null;
      }
    }
  }

  private static class MyFakeDirectory extends VirtualDirectoryImpl {
    private final String name;

    MyFakeDirectory(String name) {
      super(FSRecords.createRecord(), new VfsData.Segment(), new VfsData.DirectoryData(), null, new TempFileSystem(){
        @Override
        public FileAttributes getAttributes(@NotNull VirtualFile file) {
          return new FileAttributes(false, false, false, false, 0, 1, true);
        }
      });
      this.name = name;
    }

    @Nullable
    @Override
    public VirtualFileSystemEntry findChild(@NotNull String name) {
      return null; // hack for VCreateEvent.isValid()
    }

    @NotNull
    @Override
    public String getPath() {
      return getName();
    }

    @NotNull
    @Override
    public CharSequence getNameSequence() {
      return name;
    }

    @NotNull
    @Override
    public String getUrl() {
      return getPath();
    }
  }
}
