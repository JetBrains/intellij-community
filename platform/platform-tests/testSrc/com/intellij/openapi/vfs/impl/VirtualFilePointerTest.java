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
package com.intellij.openapi.vfs.impl;

import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.PlatformLangTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.Timings;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 *  @author dsl
 */
public class VirtualFilePointerTest extends PlatformLangTestCase {
  private VirtualFilePointerManagerImpl myVirtualFilePointerManager;
  private int numberOfPointersBefore;
  private final Disposable disposable = Disposer.newDisposable();
  private int numberOfListenersBefore;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myVirtualFilePointerManager = (VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance();
    numberOfPointersBefore = myVirtualFilePointerManager.numberOfPointers();
    numberOfListenersBefore = myVirtualFilePointerManager.numberOfListeners();
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(disposable);
    try {
      assertEquals(numberOfPointersBefore, myVirtualFilePointerManager.numberOfPointers()); // check there is no leak
      assertEquals(numberOfListenersBefore, myVirtualFilePointerManager.numberOfListeners()); // check there is no leak
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected boolean isRunInWriteAction() {
    return false;
  }

  private static class LoggingListener implements VirtualFilePointerListener {
    private final ArrayList<String> myLog = new ArrayList<String>();

    @Override
    public void beforeValidityChanged(@NotNull VirtualFilePointer[] pointers) {
      verifyPointersInCorrectState(pointers);
      myLog.add(buildMessage("before", pointers));
    }

    private static String buildMessage(@NonNls final String startMsg, VirtualFilePointer[] pointers) {
      StringBuilder buffer = new StringBuilder(startMsg);
      buffer.append(":");
      for (int i = 0; i < pointers.length; i++) {
        VirtualFilePointer pointer = pointers[i];
        final String s = Boolean.toString(pointer.isValid());
        if (i > 0) buffer.append(":");
        buffer.append(s);
      }
      return buffer.toString();
    }

    @Override
    public void validityChanged(@NotNull VirtualFilePointer[] pointers) {
      verifyPointersInCorrectState(pointers);
      myLog.add(buildMessage("after", pointers));
    }

    public ArrayList<String> getLog() {
      return myLog;
    }
  }

  public void testDelete() throws Exception {
    File tempDirectory = createTempDirectory();
    final File fileToDelete = new File(tempDirectory, "toDelete.txt");
    fileToDelete.createNewFile();
    final LoggingListener fileToDeleteListener = new LoggingListener();
    final VirtualFilePointer fileToDeletePointer = createPointerByFile(fileToDelete, fileToDeleteListener);
    assertTrue(fileToDeletePointer.isValid());
    delete(getVirtualFile(fileToDelete));
    assertFalse(fileToDeletePointer.isValid());
    assertEquals("[before:true, after:false]", fileToDeleteListener.getLog().toString());
  }

  public void testCreate() throws Exception {
    final File tempDirectory = createTempDirectory();
    final File fileToCreate = new File(tempDirectory, "toCreate.txt");
    final LoggingListener fileToCreateListener = new LoggingListener();
    final VirtualFilePointer fileToCreatePointer = createPointerByFile(fileToCreate, fileToCreateListener);
    assertFalse(fileToCreatePointer.isValid());
    fileToCreate.createNewFile();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        VirtualFileManager.getInstance().syncRefresh();
        final VirtualFile virtualFile = getVirtualFile(tempDirectory);
        virtualFile.refresh(false, true);
      }
    });
    assertTrue(fileToCreatePointer.isValid());
    assertEquals("[before:false, after:true]", fileToCreateListener.getLog().toString());
    try {
      String expectedUrl = VirtualFileManager
        .constructUrl(LocalFileSystem.PROTOCOL, fileToCreate.getCanonicalPath().replace(File.separatorChar, '/'));
      assertEquals(expectedUrl.toUpperCase(), fileToCreatePointer.getUrl().toUpperCase());
    }
    catch (IOException e) {
      fail();
    }
  }

  public void testMove() throws Exception {
    File tempDirectory = createTempDirectory();
    final File moveTarget = new File(tempDirectory, "moveTarget");
    moveTarget.mkdir();
    final File fileToMove = new File(tempDirectory, "toMove.txt");
    fileToMove.createNewFile();

    final LoggingListener fileToMoveListener = new LoggingListener();
    final VirtualFilePointer fileToMovePointer = createPointerByFile(fileToMove, fileToMoveListener);
    assertTrue(fileToMovePointer.isValid());
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final VirtualFile virtualFile = getVirtualFile(fileToMove);
        assertTrue(virtualFile.isValid());
        final VirtualFile target = getVirtualFile(moveTarget);
        assertTrue(target.isValid());
        try {
          virtualFile.move(null, target);
        }
        catch (IOException e) {
          fail();
        }
      }
    });
    assertTrue(fileToMovePointer.isValid());
    assertEquals("[]", fileToMoveListener.getLog().toString());
  }

  public void testCreate1() throws Exception {
    final File tempDirectory = createTempDirectory();
    final File fileToCreate = new File(tempDirectory, "toCreate1.txt");
    final LoggingListener fileToCreateListener = new LoggingListener();
    final VirtualFilePointer fileToCreatePointer = createPointerByFile(fileToCreate, fileToCreateListener);
    assertFalse(fileToCreatePointer.isValid());
    fileToCreate.createNewFile();
    final Runnable postRunnable = new Runnable() {
      @Override
      public void run() {
        assertTrue(fileToCreatePointer.isValid());
        assertEquals("[before:false, after:true]", fileToCreateListener.getLog().toString());
        try {
          String expectedUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, fileToCreate.getCanonicalPath().replace(File.separatorChar, '/'));
          assertEquals(expectedUrl.toUpperCase(), fileToCreatePointer.getUrl().toUpperCase());
        } catch (IOException e) {
          fail();
        }
      }
    };
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        VirtualFileManager.getInstance().syncRefresh();
        final VirtualFile virtualFile = getVirtualFile(tempDirectory);
        virtualFile.refresh(false, true);
      }
    });
    postRunnable.run();
  }

  public void testMultipleNotifications() throws Exception {
    final File tempDir = createTempDirectory();
    final File file_f1 = new File(tempDir, "f1");
    final File file_f2 = new File(tempDir, "f2");
    final LoggingListener listener = new LoggingListener();
    final VirtualFilePointer pointer_f1 = createPointerByFile(file_f1, listener);
    final VirtualFilePointer pointer_f2 = createPointerByFile(file_f2, listener);
    assertFalse(pointer_f1.isValid());
    assertFalse(pointer_f2.isValid());
    file_f1.createNewFile();
    file_f2.createNewFile();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        LocalFileSystem.getInstance().refresh(false);
      }
    });
    assertEquals("[before:false:false, after:true:true]", listener.getLog().toString());
  }

  public void testJars() throws Exception {
    final File tempDir = createTempDirectory();
    final File jarParent = new File(tempDir, "jarParent");
    jarParent.mkdir();
    final File jar = new File(jarParent, "x.jar");
    final File originalJar = new File(PathManagerEx.getTestDataPath() + "/psi/generics22/collect-2.2.jar");
    FileUtil.copy(originalJar, jar);

    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar); // Make sure we receive events when jar changes

    final VirtualFilePointer[] pointersToWatch = new VirtualFilePointer[2];
    final VirtualFilePointerListener listener = new VirtualFilePointerListener() {
      @Override
      public void beforeValidityChanged(@NotNull VirtualFilePointer[] pointers) {
        verifyPointersInCorrectState(pointersToWatch);
      }

      @Override
      public void validityChanged(@NotNull VirtualFilePointer[] pointers) {
        verifyPointersInCorrectState(pointersToWatch);
      }
    };
    final VirtualFilePointer jarParentPointer = createPointerByFile(jarParent, listener);
    final String pathInJar = jar.getPath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
    final String jarUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, pathInJar);
    final VirtualFilePointer jarPointer = myVirtualFilePointerManager.create(jarUrl, disposable, listener);
    pointersToWatch[0] = jarParentPointer;
    pointersToWatch[1] = jarPointer;
    assertTrue(jarParentPointer.isValid());
    assertTrue(jarPointer.isValid());

    jar.delete();
    jarParent.delete();
    refreshVFS();

    verifyPointersInCorrectState(pointersToWatch);
    assertFalse(jarParentPointer.isValid());
    assertFalse(jarPointer.isValid());
    UIUtil.dispatchAllInvocationEvents();

    jarParent.mkdir();
    FileUtil.copy(originalJar, jar);
    assert jar.exists();
    assert jarParent.exists();
    assert jarParent.getParentFile().exists();

    refreshVFS();

    verifyPointersInCorrectState(pointersToWatch);
    assertTrue(jarParentPointer.isValid());
    assertTrue(jarPointer.isValid());
    UIUtil.dispatchAllInvocationEvents();

    jar.delete();
    jarParent.delete();
    refreshVFS();
    UIUtil.dispatchAllInvocationEvents();

    verifyPointersInCorrectState(pointersToWatch);
    assertFalse(jarParentPointer.isValid());
    assertFalse(jarPointer.isValid());
    UIUtil.dispatchAllInvocationEvents();
  }

  public void testJars2() throws Exception {
    final File tempDir = createTempDirectory();
    final File jarParent = new File(tempDir, "jarParent");
    jarParent.mkdir();
    final File jar = new File(jarParent, "x.jar");
    final File originalJar = new File(PathManagerEx.getTestDataPath() + "/psi/generics22/collect-2.2.jar");
    FileUtil.copy(originalJar, jar);

    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar); // Make sure we receive events when jar changes

    final VirtualFilePointer[] pointersToWatch = new VirtualFilePointer[1];
    final VirtualFilePointerListener listener = new VirtualFilePointerListener() {
      @Override
      public void beforeValidityChanged(@NotNull VirtualFilePointer[] pointers) {
        verifyPointersInCorrectState(pointersToWatch);
      }

      @Override
      public void validityChanged(@NotNull VirtualFilePointer[] pointers) {
        verifyPointersInCorrectState(pointersToWatch);
      }
    };
    final String pathInJar = jar.getPath().replace(File.separatorChar, '/') + JarFileSystem.JAR_SEPARATOR;
    final String jarUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, pathInJar);
    final VirtualFilePointer jarPointer = myVirtualFilePointerManager.create(jarUrl, disposable, listener);
    pointersToWatch[0] = jarPointer;
    assertTrue(jarPointer.isValid());

    jar.delete();
    refreshVFS();
    refreshVFS();

    verifyPointersInCorrectState(pointersToWatch);
    assertFalse(jarPointer.isValid());
    UIUtil.dispatchAllInvocationEvents();

    jarParent.mkdir();
    FileUtil.copy(originalJar, jar);
    assert jar.exists();

    refreshVFS();

    verifyPointersInCorrectState(pointersToWatch);
    assertTrue(jarPointer.isValid());
    UIUtil.dispatchAllInvocationEvents();

    jar.delete();
    refreshVFS();
    UIUtil.dispatchAllInvocationEvents();

    verifyPointersInCorrectState(pointersToWatch);
    assertFalse(jarPointer.isValid());
    UIUtil.dispatchAllInvocationEvents();
  }

  private static void refreshVFS() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        VirtualFileManager.getInstance().syncRefresh();
      }
    });
    UIUtil.dispatchAllInvocationEvents();
  }

  private static void verifyPointersInCorrectState(VirtualFilePointer[] pointers) {
    for (VirtualFilePointer pointer : pointers) {
      final VirtualFile file = pointer.getFile();
      assertTrue(file == null || file.isValid());
    }
  }

  private VirtualFilePointer createPointerByFile(final File file, final VirtualFilePointerListener fileListener) throws IOException {
    final String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, file.getCanonicalPath().replace(File.separatorChar, '/'));
    final VirtualFile vFile = refreshAndFind(url);
    return vFile == null
           ? myVirtualFilePointerManager.create(url, disposable, fileListener)
           : myVirtualFilePointerManager.create(vFile, disposable, fileListener);
  }

  public void testFilePointerUpdate() throws Exception {
    final File tempDir = createTempDirectory();
    final File file = new File(tempDir, "f1");

    final VirtualFilePointer pointer = createPointerByFile(file, null);

    assertFalse(pointer.isValid());

    boolean created = file.createNewFile();
    assertTrue(created);


    doVfsRefresh();

    assertTrue(pointer.isValid());

    boolean deleted = file.delete();
    assertTrue(deleted);

    doVfsRefresh();
    assertFalse(pointer.isValid());
  }

  public void testContainerCreateDeletePerformance() throws Exception {
    PlatformTestUtil.startPerformanceTest("VF container create/delete",200, new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
        Disposable parent = Disposer.newDisposable();
        for (int i = 0; i < 10000; i++) {
          myVirtualFilePointerManager.createContainer(parent);
        }
        Disposer.dispose(parent);
      }
    }).cpuBound().assertTiming();
  }

  private static void doVfsRefresh() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        LocalFileSystem.getInstance().refresh(false);
      }
    });
  }

  public void testDoubleDispose() throws IOException {
    final File tempDir = createTempDirectory();
    final File file = new File(tempDir, "f1");
    boolean created = file.createNewFile();
    assertTrue(created);


    final String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, file.getCanonicalPath().replace(File.separatorChar, '/'));
    final VirtualFile vFile = refreshAndFind(url);

    Disposable disposable = Disposer.newDisposable();
    final VirtualFilePointer pointer = myVirtualFilePointerManager.create(vFile, disposable, new VirtualFilePointerListener() {
      @Override
      public void beforeValidityChanged(@NotNull VirtualFilePointer[] pointers) {
      }

      @Override
      public void validityChanged(@NotNull VirtualFilePointer[] pointers) {
      }
    });


    assertTrue(pointer.isValid());

    Disposer.dispose(disposable);
    assertFalse(pointer.isValid());
  }

  private static VirtualFile refreshAndFind(@NotNull final String url) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        return VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
      }
    });
  }

  public void testThreadsPerformance() throws IOException, InterruptedException {
    final File ioTempDir = createTempDirectory();
    final File ioPtrBase = new File(ioTempDir, "parent");
    final File ioPtr = new File(ioPtrBase, "f1");
    final File ioSand = new File(ioTempDir, "sand");
    final File ioSandPtr = new File(ioSand, "f2");
    ioSandPtr.getParentFile().mkdirs();
    ioSandPtr.createNewFile();
    ioPtr.getParentFile().mkdirs();
    ioPtr.createNewFile();

    doVfsRefresh();
    final VirtualFilePointer pointer = createPointerByFile(ioPtr, null);
    assertTrue(pointer.isValid());
    final VirtualFile virtualFile = pointer.getFile();
    assertNotNull(virtualFile);
    assertTrue(virtualFile.isValid());

    VirtualFileAdapter listener = new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent event) {
        stressRead(pointer);
      }

      @Override
      public void fileDeleted(VirtualFileEvent event) {
        stressRead(pointer);
      }
    };
    Disposable disposable = Disposer.newDisposable();
    VirtualFileManager.getInstance().addVirtualFileListener(listener, disposable);
    int N = Timings.adjustAccordingToMySpeed(1000, false);
    System.out.println("N = " + N);
    for (int i=0;i< N;i++) {
      assertNotNull(pointer.getFile());
      FileUtil.delete(ioPtrBase);
      doVfsRefresh();

      // ptr is now null, cached as map

      final VirtualFile v = LocalFileSystem.getInstance().findFileByIoFile(ioSandPtr);
      new WriteCommandAction.Simple(getProject()) {
        @Override
        protected void run() throws Throwable {
          v.delete(this); //inc FS modCount
          LocalFileSystem.getInstance().findFileByIoFile(ioSand).createChildData(this, ioSandPtr.getName());
        }
      }.execute().throwException();

      // ptr is still null

      assertTrue(ioPtrBase.mkdirs());
      assertTrue(ioPtr.createNewFile());

      stressRead(pointer);
      doVfsRefresh();
    }

    Disposer.dispose(disposable); // unregister listener early
  }

  private static void stressRead(@NotNull final VirtualFilePointer pointer) {
    for (int i = 0; i < 10; i++) {
    JobLauncher.getInstance().submitToJobThread(Job.DEFAULT_PRIORITY, new Runnable() {
      @Override
      public void run() {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              VirtualFile file = pointer.getFile();
              if (file != null && !file.isValid()) {
                throw new IncorrectOperationException("I've caught it. I am that good");
              }
            }
          });
      }
    }, new Consumer<Future>() {
                                                                  @Override
                                                                  public void consume(Future future) {
                                                                    try {
                                                                      future.get();
                                                                    }
                                                                    catch (Exception e) {
                                                                      throw new RuntimeException(e);
                                                                    }
                                                                  }
                                                                });
    }
  }

  public void testManyPointersUpdatePerformance() throws IOException {
    FilePointerPartNode.pushDebug(false, disposable);
    LoggingListener listener = new LoggingListener();
    final List<VFileEvent> events = new ArrayList<VFileEvent>();
    final File ioTempDir = createTempDirectory();
    final VirtualFile temp = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioTempDir);
    for (int i=0; i<100000; i++) {
      myVirtualFilePointerManager.create(VfsUtilCore.pathToUrl("/a/b/c/d/" + i), disposable, listener);
      events.add(new VFileCreateEvent(this, temp, "xxx" + i, false, true));
    }
    PlatformTestUtil.startPerformanceTest("vfp update", 10000, new ThrowableRunnable() {
      @Override
      public void run() throws Throwable {
        for (int i=0; i<100; i++) {
          // simulate VFS refresh events since launching the actual refresh is too slow
          myVirtualFilePointerManager.before(events);
          myVirtualFilePointerManager.after(events);
        }
      }
    }).assertTiming();
  }
  public void testMultipleCreationOfTheSamePointerPerformance() throws IOException {
    FilePointerPartNode.pushDebug(false, disposable);
    final LoggingListener listener = new LoggingListener();
    final VirtualFilePointer thePointer = myVirtualFilePointerManager.create(VfsUtilCore.pathToUrl("/a/b/c/d/e"), disposable, listener);
    TempFileSystem.getInstance();
    PlatformTestUtil.startPerformanceTest("same url vfp create", 500, new ThrowableRunnable() {
      @Override
      public void run() throws Throwable {
        for (int i=0; i<1000000; i++) {
          VirtualFilePointer pointer = myVirtualFilePointerManager.create(VfsUtilCore.pathToUrl("/a/b/c/d/e"), disposable, listener);
          assertSame(pointer, thePointer);
        }
      }
    }).assertTiming();
  }

  public void testTwoPointersBecomeOneAfterFileRenamedUnderTheOtherName() throws IOException {
    final File tempDir = createTempDirectory();
    final File f1 = new File(tempDir, "f1");
    boolean created = f1.createNewFile();
    assertTrue(created);

    final String url1 = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, f1.getCanonicalPath().replace(File.separatorChar, '/'));
    final VirtualFile vFile1 = refreshAndFind(url1);

    VirtualFilePointer pointer1 = myVirtualFilePointerManager.create(url1, disposable, null);
    assertTrue(pointer1.isValid());
    String url2 = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, tempDir.getCanonicalPath().replace(File.separatorChar, '/')+"/f2");
    VirtualFilePointer pointer2 = myVirtualFilePointerManager.create(url2, disposable, null);
    assertFalse(pointer2.isValid());

    rename(vFile1, "f2");

    assertTrue(pointer1.isValid());
    assertTrue(pointer2.isValid());
  }
}
