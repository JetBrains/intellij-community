/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryUtil;
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
import com.intellij.testFramework.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 *  @author dsl
 */
public class VirtualFilePointerTest extends PlatformTestCase {
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

  private static class LoggingListener implements VirtualFilePointerListener {
    private final ArrayList<String> myLog = new ArrayList<>();

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
    VfsTestUtil.deleteFile(getVirtualFile(fileToDelete));
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
    ApplicationManager.getApplication().runWriteAction(() -> {
      VirtualFileManager.getInstance().syncRefresh();
      final VirtualFile virtualFile = getVirtualFile(tempDirectory);
      virtualFile.refresh(false, true);
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

  public void testUrlsHavingOnlyStartingSlashInCommon() throws Exception {
    VirtualFilePointer p1 = myVirtualFilePointerManager.create("file:///a/p1", disposable, null);
    VirtualFilePointer p2 = myVirtualFilePointerManager.create("file:///b/p2", disposable, null);
    final LightVirtualFile root = new LightVirtualFile("/");
    LightVirtualFile a = createLightFile(root, "a");
    LightVirtualFile b = createLightFile(root, "b");
    assertSameElements(myVirtualFilePointerManager.getPointersUnder(a, "p1"), p1);
    assertSameElements(myVirtualFilePointerManager.getPointersUnder(b, "p2"), p2);
  }

  public void testUrlsHavingOnlyStartingSlashInCommonAndInvalidUrlBetweenThem() throws Exception {
    VirtualFilePointer p1 = myVirtualFilePointerManager.create("file:///a/p1", disposable, null);
    myVirtualFilePointerManager.create("file://invalid/path", disposable, null);
    VirtualFilePointer p2 = myVirtualFilePointerManager.create("file:///b/p2", disposable, null);
    final LightVirtualFile root = new LightVirtualFile("/");
    LightVirtualFile a = createLightFile(root, "a");
    LightVirtualFile b = createLightFile(root, "b");
    assertSameElements(myVirtualFilePointerManager.getPointersUnder(a, "p1"), p1);
    assertSameElements(myVirtualFilePointerManager.getPointersUnder(b, "p2"), p2);
  }

  @NotNull
  private static LightVirtualFile createLightFile(final LightVirtualFile parent, final String name) {
    return new LightVirtualFile(name) {
      @Override
      public VirtualFile getParent() {
        return parent;
      }
    };
  }

  public void testPathNormalization() throws Exception {
    checkFileName("///", "");
  }
  public void testPathNormalization2() throws Exception {
    checkFileName("\\\\", "/");
  }
  public void testPathNormalization3() throws Exception {
    checkFileName("//", "/////");
  }

  private void checkFileName(String prefix, String suffix) throws IOException {
    final File tempDirectory = createTempDirectory();

    final VirtualFile temp = getVirtualFile(tempDirectory);
    String name = "toCreate.txt";
    final VirtualFilePointer fileToCreatePointer = createPointerByFile(new File(tempDirectory.getPath() + prefix + name +suffix), null);
    assertFalse(fileToCreatePointer.isValid());
    assertNull(fileToCreatePointer.getFile());

    VirtualFile child = createChildData(temp, name);

    assertTrue(fileToCreatePointer.isValid());
    assertEquals(child, fileToCreatePointer.getFile());

    VfsTestUtil.deleteFile(child);
    assertFalse(fileToCreatePointer.isValid());
    assertNull(fileToCreatePointer.getFile());
  }

  public void testMovePointedFile() throws Exception {
    File tempDirectory = createTempDirectory();
    final File moveTarget = new File(tempDirectory, "moveTarget");
    moveTarget.mkdir();
    final File fileToMove = new File(tempDirectory, "toMove.txt");
    fileToMove.createNewFile();

    final LoggingListener fileToMoveListener = new LoggingListener();
    final VirtualFilePointer fileToMovePointer = createPointerByFile(fileToMove, fileToMoveListener);
    assertTrue(fileToMovePointer.isValid());
    ApplicationManager.getApplication().runWriteAction(() -> {
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
    });
    assertTrue(fileToMovePointer.isValid());
    assertEquals("[]", fileToMoveListener.getLog().toString());
  }

  public void testMoveFileUnderExistingPointer() throws Exception {
    File tempDirectory = createTempDirectory();
    final File moveTarget = new File(tempDirectory, "moveTarget");
    moveTarget.mkdir();
    final File fileToMove = new File(tempDirectory, "toMove.txt");
    fileToMove.createNewFile();

    final LoggingListener listener = new LoggingListener();
    final VirtualFilePointer fileToMoveTargetPointer = createPointerByFile(new File(moveTarget, fileToMove.getName()), listener);
    assertFalse(fileToMoveTargetPointer.isValid());
    ApplicationManager.getApplication().runWriteAction(() -> {
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
    });
    assertTrue(fileToMoveTargetPointer.isValid());
    assertEquals("[before:false, after:true]", listener.getLog().toString());
  }

  public void testMovePointedFileUnderAnotherPointer() throws Exception {
    File tempDirectory = createTempDirectory();
    final File moveTarget = new File(tempDirectory, "moveTarget");
    moveTarget.mkdir();
    final File fileToMove = new File(tempDirectory, "toMove.txt");
    fileToMove.createNewFile();

    final LoggingListener listener = new LoggingListener();
    final LoggingListener targetListener = new LoggingListener();

    final VirtualFilePointer fileToMovePointer = createPointerByFile(fileToMove, listener);
    final VirtualFilePointer fileToMoveTargetPointer = createPointerByFile(new File(moveTarget, fileToMove.getName()), targetListener);

    assertFalse(fileToMoveTargetPointer.isValid());
    ApplicationManager.getApplication().runWriteAction(() -> {
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
    });
    assertTrue(fileToMovePointer.isValid());
    assertTrue(fileToMoveTargetPointer.isValid());
    assertEquals("[]", listener.getLog().toString());
    assertEquals("[before:false, after:true]", targetListener.getLog().toString());
  }

  public void testRenamingPointedFile() throws IOException {
    final File tempDir = createTempDirectory();
    final File file = new File(tempDir, "f1");
    assertTrue(file.createNewFile());

    final LoggingListener listener = new LoggingListener();
    VirtualFilePointer pointer = createPointerByFile(file, listener);
    assertTrue(pointer.isValid());
    rename(getVirtualFile(file), "f2");
    assertTrue(pointer.isValid());
    assertEquals("[]", listener.getLog().toString());
  }

  public void testRenamingFileUnderTheExistingPointer() throws IOException {
    final File tempDir = createTempDirectory();
    final File file = new File(tempDir, "f1");
    assertTrue(file.createNewFile());

    final LoggingListener listener = new LoggingListener();
    VirtualFilePointer pointer = createPointerByFile(new File(file.getParent(), "f2"), listener);
    assertFalse(pointer.isValid());
    rename(getVirtualFile(file), "f2");
    assertTrue(pointer.isValid());
    assertEquals("[before:false, after:true]", listener.getLog().toString());
  }

  public void testTwoPointersBecomeOneAfterFileRenamedUnderTheOtherName() throws IOException {
    final File tempDir = createTempDirectory();
    final File f1 = new File(tempDir, "f1");
    boolean created = f1.createNewFile();
    assertTrue(created);

    final String url1 = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, f1.getCanonicalPath().replace(File.separatorChar, '/'));
    final VirtualFile vFile1 = refreshAndFind(url1);

    final LoggingListener listener1 = new LoggingListener();
    VirtualFilePointer pointer1 = myVirtualFilePointerManager.create(url1, disposable, listener1);
    assertTrue(pointer1.isValid());
    String url2 = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, tempDir.getCanonicalPath().replace(File.separatorChar, '/')+"/f2");
    final LoggingListener listener2 = new LoggingListener();
    VirtualFilePointer pointer2 = myVirtualFilePointerManager.create(url2, disposable, listener2);
    assertFalse(pointer2.isValid());

    rename(vFile1, "f2");

    assertTrue(pointer1.isValid());
    assertTrue(pointer2.isValid());

    assertEquals("[]", listener1.getLog().toString());
    assertEquals("[before:false, after:true]", listener2.getLog().toString());
  }

  public void testCreate1() throws Exception {
    final File tempDirectory = createTempDirectory();
    final File fileToCreate = new File(tempDirectory, "toCreate1.txt");
    final LoggingListener fileToCreateListener = new LoggingListener();
    final VirtualFilePointer fileToCreatePointer = createPointerByFile(fileToCreate, fileToCreateListener);
    assertFalse(fileToCreatePointer.isValid());
    fileToCreate.createNewFile();
    final Runnable postRunnable = () -> {
      assertTrue(fileToCreatePointer.isValid());
      assertEquals("[before:false, after:true]", fileToCreateListener.getLog().toString());
      try {
        String expectedUrl = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, fileToCreate.getCanonicalPath().replace(File.separatorChar, '/'));
        assertEquals(expectedUrl.toUpperCase(), fileToCreatePointer.getUrl().toUpperCase());
      } catch (IOException e) {
        fail();
      }
    };
    ApplicationManager.getApplication().runWriteAction(() -> {
      VirtualFileManager.getInstance().syncRefresh();
      final VirtualFile virtualFile = getVirtualFile(tempDirectory);
      virtualFile.refresh(false, true);
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
    ApplicationManager.getApplication().runWriteAction(() -> LocalFileSystem.getInstance().refresh(false));
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
    ApplicationManager.getApplication().runWriteAction(() -> {
      VirtualFileManager.getInstance().syncRefresh();
    });
    UIUtil.dispatchAllInvocationEvents();
  }

  private static void verifyPointersInCorrectState(VirtualFilePointer[] pointers) {
    for (VirtualFilePointer pointer : pointers) {
      final VirtualFile file = pointer.getFile();
      assertTrue(file == null || file.isValid());
    }
  }

  private VirtualFilePointer createPointerByFile(@NotNull File file, @Nullable VirtualFilePointerListener fileListener) throws IOException {
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


    doVfsRefresh(tempDir);

    assertTrue(pointer.isValid());

    boolean deleted = file.delete();
    assertTrue(deleted);

    doVfsRefresh(tempDir);
    assertFalse(pointer.isValid());
  }

  public void testContainerCreateDeletePerformance() throws Exception {
    PlatformTestUtil.startPerformanceTest("VF container create/delete", 200, () -> {
      Disposable parent = Disposer.newDisposable();
      for (int i = 0; i < 10000; i++) {
        myVirtualFilePointerManager.createContainer(parent);
      }
      Disposer.dispose(parent);
    }).cpuBound().useLegacyScaling().assertTiming();
  }

  private static void doVfsRefresh(File dir) {
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir).refresh(false, true);
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
    return WriteCommandAction.runWriteCommandAction(null, (Computable<VirtualFile>)() -> VirtualFileManager.getInstance().refreshAndFindFileByUrl(url));
  }

  public void testThreadsPerformance() throws IOException, InterruptedException, TimeoutException, ExecutionException {
    final File ioTempDir = createTempDirectory();
    final File ioPtrBase = new File(ioTempDir, "parent");
    final File ioPtr = new File(ioPtrBase, "f1");
    final File ioSand = new File(ioTempDir, "sand");
    final File ioSandPtr = new File(ioSand, "f2");
    ioSandPtr.getParentFile().mkdirs();
    ioSandPtr.createNewFile();
    ioPtr.getParentFile().mkdirs();
    ioPtr.createNewFile();

    doVfsRefresh(ioTempDir);
    final VirtualFilePointer pointer = createPointerByFile(ioPtr, null);
    assertTrue(pointer.isValid());
    final VirtualFile virtualFile = pointer.getFile();
    assertNotNull(virtualFile);
    assertTrue(virtualFile.isValid());
    final Collection<Job<Void>> reads = ContainerUtil.newConcurrentSet();
    VirtualFileAdapter listener = new VirtualFileAdapter() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        stressRead(pointer, reads);
      }

      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        stressRead(pointer, reads);
      }
    };
    Disposable disposable = Disposer.newDisposable();
    VirtualFileManager.getInstance().addVirtualFileListener(listener, disposable);
    try {
      int N = Timings.adjustAccordingToMySpeed(1000, false);
      System.out.println("N = " + N);
      for (int i=0;i< N;i++) {
        assertNotNull(pointer.getFile());
        FileUtil.delete(ioPtrBase);
        doVfsRefresh(ioTempDir);

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

        stressRead(pointer, reads);
        doVfsRefresh(ioTempDir);
      }
    }
    finally {
      Disposer.dispose(disposable); // unregister listener early
      for (Job<Void> read : reads) {
        while (!read.isDone()) {
          read.waitForCompletion(1000);
        }
      }
    }
  }

  private static void stressRead(@NotNull final VirtualFilePointer pointer, @NotNull final Collection<Job<Void>> reads) {
    for (int i = 0; i < 10; i++) {
      final AtomicReference<Job<Void>> reference = new AtomicReference<>();
      reference.set(JobLauncher.getInstance().submitToJobThread(() -> ApplicationManager.getApplication().runReadAction(() -> {
        VirtualFile file = pointer.getFile();
        if (file != null && !file.isValid()) {
          throw new IncorrectOperationException("I've caught it. I am that good");
        }
      }), future -> {
        try {
          future.get();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
        finally {
          reads.remove(reference.get());
        }
      }));
      reads.add(reference.get());
    }
  }

  public void testManyPointersUpdatePerformance() throws IOException {
    LoggingListener listener = new LoggingListener();
    final List<VFileEvent> events = new ArrayList<>();
    final File ioTempDir = createTempDirectory();
    final VirtualFile temp = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioTempDir);
    for (int i=0; i<100000; i++) {
      myVirtualFilePointerManager.create(VfsUtilCore.pathToUrl("/a/b/c/d/" + i), disposable, listener);
      events.add(new VFileCreateEvent(this, temp, "xxx" + i, false, true));
    }
    PlatformTestUtil.startPerformanceTest("vfp update", 10000, () -> {
      for (int i=0; i<100; i++) {
        // simulate VFS refresh events since launching the actual refresh is too slow
        myVirtualFilePointerManager.before(events);
        myVirtualFilePointerManager.after(events);
      }
    }).useLegacyScaling().assertTiming();
  }

  public void testMultipleCreationOfTheSamePointerPerformance() throws IOException {
    final LoggingListener listener = new LoggingListener();
    final String url = VfsUtilCore.pathToUrl("/a/b/c/d/e");
    final VirtualFilePointer thePointer = myVirtualFilePointerManager.create(url, disposable, listener);
    TempFileSystem.getInstance();
    PlatformTestUtil.startPerformanceTest("same url vfp create", 5000, () -> {
      for (int i=0; i<10000000; i++) {
        VirtualFilePointer pointer = myVirtualFilePointerManager.create(url, disposable, listener);
        assertSame(pointer, thePointer);
      }
    }).useLegacyScaling().assertTiming();
  }

  public void testCidrCrazyAddCreateRenames() throws IOException {
    File tempDirectory = createTempDirectory();
    final VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory);

    VirtualFile dir1 = createChildDirectory(root, "dir1");
    VirtualFile dir2 = createChildDirectory(root, "dir2");

    PsiTestUtil.addSourceRoot(getModule(), dir1);
    PsiTestUtil.addLibrary(getModule(), "mylib", "", new String[]{dir2.getPath()}, ArrayUtil.EMPTY_STRING_ARRAY);

    assertSourceIs(dir1);
    assertLibIs(dir2);

    delete(dir1);
    assertSourceIs(null); // srcDir deleted, no more sources
    assertLibIs(dir2);    // libDir stays the same
    myVirtualFilePointerManager.assertConsistency();
    rename(dir2, "dir1");
    myVirtualFilePointerManager.assertConsistency();
    assertSourceIs(dir2); // srcDir re-appeared, sources are "dir1"
    assertLibIs(dir2);    // libDir renamed, libs are "dir1" now

    rename(dir2, "dir2");
    assertSourceIs(dir2); // srcDir renamed, sources are "dir2" now
    assertLibIs(dir2);    // libDir renamed, libs are "dir2" now

    dir1 = createChildDirectory(root, "dir1");
    assertNotNull(dir1);
    assertSourceIs(dir2); // srcDir stays the same
    assertLibIs(dir2);    // libDir stays the same

    PsiTestUtil.removeAllRoots(getModule(), getTestProjectJdk());
  }

  private void assertLibIs(VirtualFile dir2) {
    VirtualFile libRoot = assertOneElement(
      OrderEntryUtil.getModuleLibraries(ModuleRootManager.getInstance(getModule())).get(0).getFiles(OrderRootType.CLASSES));
    assertEquals(dir2, libRoot);
  }

  private void assertSourceIs(VirtualFile dir1) {
    VirtualFile[] roots = ModuleRootManager.getInstance(getModule()).getSourceRoots();
    VirtualFile[] expected = dir1 == null ? VirtualFile.EMPTY_ARRAY : new VirtualFile[]{dir1};
    assertOrderedEquals(roots, expected);
  }

  public void testTwoPointersMergingIntoOne() throws IOException {
    File tempDirectory = createTempDirectory();
    final VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory);

    VirtualFile dir1 = createChildDirectory(root, "dir1");
    VirtualFile dir2 = createChildDirectory(root, "dir2");

    VirtualFilePointer p1 = myVirtualFilePointerManager.create(dir1, disposable, null);
    VirtualFilePointer p2 = myVirtualFilePointerManager.create(dir2, disposable, null);
    assertTrue(p1.isValid());
    assertEquals(dir1, p1.getFile());
    assertTrue(p2.isValid());
    assertEquals(dir2, p2.getFile());

    delete(dir1);
    assertEquals(null, p1.getFile());
    assertEquals(dir2, p2.getFile());

    rename(dir2, "dir1");
    assertEquals(dir2, p1.getFile());
    assertEquals(dir2, p2.getFile());

    rename(dir2, "dir2");
    assertEquals(dir2, p1.getFile());
    assertEquals(dir2, p2.getFile());

    createChildDirectory(root, "dir1");
    assertEquals(dir2, p1.getFile());
    assertEquals(dir2, p2.getFile());
  }

  public void testVirtualPointersMustBeAlreadyUpToDateInVFSChangeListeners() throws IOException {
    File tempDirectory = createTempDirectory();
    final VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory);

    VirtualFile dir1 = createChildDirectory(root, "dir1");
    VirtualFile file = createChildData(dir1, "x.txt");
    setFileText(file, "xxxxxx");

    PsiTestUtil.addLibrary(getModule(), dir1.getPath());

    VirtualFileAdapter listener = new VirtualFileAdapter() {
      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        ProjectRootManager.getInstance(getProject()).getFileIndex().getModuleForFile(dir1);
      }
    };
    LocalFileSystem.getInstance().addVirtualFileListener(listener);
    Disposer.register(disposable, () -> LocalFileSystem.getInstance().removeVirtualFileListener(listener));

    assertTrue(FileUtil.delete(new File(dir1.getPath())));
    System.out.println("deleted "+dir1);

    try {
      while (root.findChild("dir1") != null) {
        UIUtil.dispatchAllInvocationEvents();
        LocalFileSystem.getInstance().refresh(false);
      }
    }
    finally {
      ApplicationManager.getApplication().runWriteAction(() -> {
        Library library = LibraryUtil.findLibrary(getModule(), "dir1");
        LibraryTable.ModifiableModel model = library.getTable().getModifiableModel();
        model.removeLibrary(library);
        model.commit();
      });


      PsiTestUtil.removeAllRoots(getModule(), null);
    }
  }

  public void testDotDot() throws IOException {
    File tempDirectory = createTempDirectory();
    final VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDirectory);

    VirtualFile dir1 = createChildDirectory(root, "dir1");
    VirtualFile dir2 = createChildDirectory(root, "dir2");
    VirtualFile file = createChildData(dir1, "x.txt");

    VirtualFilePointer pointer = myVirtualFilePointerManager.create(dir2.getUrl() + "/../" + dir1.getName() + "/" + file.getName(), disposable, null);
    assertEquals(file, pointer.getFile());
  }

  public void testAlienVirtualFileSystemPointerRemovedFromUrlToIdentityCacheOnDispose() throws IOException {
    VirtualFile mockVirtualFile = new MockVirtualFile("test_name", "test_text");
    Disposable disposable1 = Disposer.newDisposable();
    final VirtualFilePointer pointer = VirtualFilePointerManager.getInstance().create(mockVirtualFile, disposable1, null);

    assertInstanceOf(pointer, IdentityVirtualFilePointer.class);
    assertTrue(pointer.isValid());

    VirtualFile virtualFileWithSameUrl = new MockVirtualFile("test_name", "test_text");
    VirtualFilePointer updatedPointer = VirtualFilePointerManager.getInstance().create(virtualFileWithSameUrl, disposable1, null);

    assertInstanceOf(updatedPointer, IdentityVirtualFilePointer.class);

    assertEquals(1, ((VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance()).numberOfCachedUrlToIdentity());

    Disposer.dispose(disposable1);

    assertEquals(0, ((VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance()).numberOfCachedUrlToIdentity());
  }
}
