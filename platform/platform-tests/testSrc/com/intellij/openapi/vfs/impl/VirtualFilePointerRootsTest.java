// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryUtil;
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
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RunFirst
@SkipSlowTestLocally
public class VirtualFilePointerRootsTest extends HeavyPlatformTestCase {
  private final Disposable disposable = Disposer.newDisposable();
  private VirtualFilePointerManagerImpl myVirtualFilePointerManager;
  private int numberOfPointersBefore;
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
    try {
      Disposer.dispose(disposable);
      assertEquals(numberOfPointersBefore, myVirtualFilePointerManager.numberOfPointers());
      assertEquals(numberOfListenersBefore, myVirtualFilePointerManager.numberOfListeners());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myVirtualFilePointerManager = null;
      super.tearDown();
    }
  }

  public void testContainerCreateDeletePerformance() {
    PlatformTestUtil.startPerformanceTest("VF container create/delete", 1000, () -> {
      Disposable parent = Disposer.newDisposable();
      for (int i = 0; i < 100_000; i++) {
        myVirtualFilePointerManager.createContainer(parent);
      }
      Disposer.dispose(parent);
    }).assertTiming();
  }

  public void testMultipleCreatePointerWithTheSameUrlPerformance() throws IOException {
    VirtualFilePointerListener listener = new VirtualFilePointerListener() { };
    File f = new File(createTempDirectory(), "a/b/c/d");
    String url = VfsUtilCore.pathToUrl(f.getPath());
    VirtualFilePointer thePointer = myVirtualFilePointerManager.create(url, disposable, listener);
    assertNotNull(TempFileSystem.getInstance());
    PlatformTestUtil.startPerformanceTest("same url vfp create", 9000, () -> {
      for (int i = 0; i < 1_000_000; i++) {
        VirtualFilePointer pointer = myVirtualFilePointerManager.create(url, disposable, listener);
        assertSame(pointer, thePointer);
      }
    }).assertTiming();
  }

  public void testMultipleCreatePointerWithTheSameFilePerformance() throws IOException {
    VirtualFilePointerListener listener = new VirtualFilePointerListener() { };
    File f = new File(createTempDirectory(), "a/b/c/d");
    assertTrue(f.mkdirs());
    VirtualFile v = refreshAndFindFile(f);
    VirtualFilePointer thePointer = myVirtualFilePointerManager.create(v, disposable, listener);
    assertNotNull(TempFileSystem.getInstance());
    PlatformTestUtil.startPerformanceTest("same url vfp create", 9000, () -> {
      for (int i = 0; i < 10_000_000; i++) {
        VirtualFilePointer pointer = myVirtualFilePointerManager.create(v, disposable, listener);
        assertSame(pointer, thePointer);
      }
    }).assertTiming();
  }

  public void testManyPointersUpdatePerformance() throws IOException {
    VirtualFilePointerListener listener = new VirtualFilePointerListener() { };
    VirtualFile temp = getVirtualFile(createTempDirectory());
    List<VFileEvent> events = new ArrayList<>();
    myVirtualFilePointerManager.shelveAllPointersIn(() -> {
      for (int i = 0; i < 100_000; i++) {
        myVirtualFilePointerManager.create(VfsUtilCore.pathToUrl("/a/b/c/d/" + i), disposable, listener);
        String name = "xxx" + (i%20);
        events.add(new VFileCreateEvent(this, temp, name, true, null, null, true, null));
      }
      PlatformTestUtil.startPerformanceTest("vfp update", 7_500, () -> {
        for (int i = 0; i < 100; i++) {
          // simulate VFS refresh events since launching the actual refresh is too slow
          AsyncFileListener.ChangeApplier applier = myVirtualFilePointerManager.prepareChange(events);
          applier.beforeVfsChange();
          applier.afterVfsChange();
        }
      }).assertTiming();
    });
  }

  public void testCidrCrazyAddCreateRenames() throws IOException {
    VirtualFile root = getVirtualFile(createTempDirectory());
    VirtualFile dir1 = WriteAction.compute(() -> root.createChildDirectory(this, "dir1"));
    VirtualFile dir2 = WriteAction.compute(() -> root.createChildDirectory(this, "dir2"));

    PsiTestUtil.addSourceRoot(getModule(), dir1);
    PsiTestUtil.addLibrary(getModule(), "myLib", "", new String[]{dir2.getPath()}, ArrayUtilRt.EMPTY_STRING_ARRAY);
    assertSourceIs(dir1);
    assertLibIs(dir2);

    WriteAction.run(() -> dir1.delete(this));
    assertSourceIs(null); // srcDir deleted, no more sources
    assertLibIs(dir2);    // libDir stays the same
    myVirtualFilePointerManager.assertConsistency();
    WriteAction.run(() -> dir2.rename(this, "dir1"));
    myVirtualFilePointerManager.assertConsistency();
    assertSourceIs(dir2); // srcDir re-appeared, sources are "dir1"
    assertLibIs(dir2);    // libDir renamed, libs are "dir1" now

    WriteAction.run(() -> dir2.rename(this, "dir2"));
    assertSourceIs(dir2); // srcDir renamed, sources are "dir2" now
    assertLibIs(dir2);    // libDir renamed, libs are "dir2" now

    WriteAction.compute(() -> root.createChildDirectory(this, "dir1"));
    assertSourceIs(dir2); // srcDir stays the same
    assertLibIs(dir2);    // libDir stays the same

    ModuleRootModificationUtil.updateModel(getModule(), model -> model.clear());
  }

  private void assertSourceIs(VirtualFile dir) {
    VirtualFile[] roots = ModuleRootManager.getInstance(getModule()).getSourceRoots();
    if (dir == null) {
      assertEmpty(roots);
    }
    else {
      VirtualFile root = assertOneElement(roots);
      assertEquals(dir, root);
    }
  }

  private void assertLibIs(VirtualFile dir) {
    VirtualFile[] roots = OrderEntryUtil.getModuleLibraries(ModuleRootManager.getInstance(getModule())).get(0).getFiles(OrderRootType.CLASSES);
    VirtualFile root = assertOneElement(roots);
    assertEquals(dir, root);
  }

  public void testVirtualPointersMustBeAlreadyUpToDateInVFSChangeListeners() throws IOException {
    VirtualFile root = getVirtualFile(createTempDirectory());
    VirtualFile dir1 = WriteAction.compute(() -> root.createChildDirectory(this, "dir1"));
    WriteAction.run(() -> VfsUtil.saveText(dir1.createChildData(this, "x.txt"), "xx.xx.xx"));

    PsiTestUtil.addLibrary(getModule(), dir1.getPath());

    VirtualFileListener listener = new VirtualFileListener() {
      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        ProjectRootManager.getInstance(getProject()).getFileIndex().getModuleForFile(dir1);
      }
    };
    LocalFileSystem.getInstance().addVirtualFileListener(listener);
    Disposer.register(disposable, () -> LocalFileSystem.getInstance().removeVirtualFileListener(listener));

    assertTrue(FileUtil.delete(new File(dir1.getPath())));
    LOG.debug("deleted " + dir1);

    try {
      while (root.findChild("dir1") != null) {
        UIUtil.dispatchAllInvocationEvents();
        LocalFileSystem.getInstance().refresh(false);
      }
    }
    finally {
      WriteAction.run(() -> {
        Library library = PlatformTestUtil.notNull(LibraryUtil.findLibrary(getModule(), "dir1"));
        LibraryTable.ModifiableModel model = library.getTable().getModifiableModel();
        model.removeLibrary(library);
        model.commit();
      });
      PsiTestUtil.removeAllRoots(getModule(), null);
    }
  }
}