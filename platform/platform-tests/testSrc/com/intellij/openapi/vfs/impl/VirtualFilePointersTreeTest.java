// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lightweight test for building and processing tree of {@link FilePointerPartNode}s. It doesn't create real files.
 */
public class VirtualFilePointersTreeTest extends LightPlatformTestCase {
  private VirtualFilePointerManagerImpl myVirtualFilePointerManager;
  private VirtualFilePointerListener myDummyListener;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myVirtualFilePointerManager = (VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance();
    myDummyListener = new VirtualFilePointerListener() {};
  }

  public void testRecursivePointersForSubdirectories() {
    VirtualFilePointer parentPointer = createRecursivePointer("file:///parent");
    VirtualFilePointer dirPointer = createRecursivePointer("file:///parent/dir");
    VirtualFilePointer subdirPointer = createRecursivePointer("file:///parent/dir/subdir");
    VirtualFilePointer filePointer = createPointer("file:///parent/dir/subdir/file.txt");
    LightVirtualFile root = new LightVirtualFile("/");
    LightVirtualFile parent = new LightVirtualFileWithParent("parent", root);
    LightVirtualFile dir = new LightVirtualFileWithParent("dir", parent);
    LightVirtualFile subdir = new LightVirtualFileWithParent("subdir", dir);
    assertPointersUnder(subdir, "xxx.txt", parentPointer, dirPointer, subdirPointer);
    assertPointersUnder(subdir, "", parentPointer, dirPointer, subdirPointer, filePointer);
    assertPointersUnder(dir, "", parentPointer, dirPointer, subdirPointer, filePointer);
    assertPointersUnder(parent, "", parentPointer, dirPointer, subdirPointer, filePointer);
  }

  public void testRecursivePointersForDirectoriesWithCommonPrefix() {
    VirtualFilePointer parentPointer = createRecursivePointer("file:///parent");
    VirtualFilePointer dir1Pointer = createRecursivePointer("file:///parent/dir1");
    VirtualFilePointer dir2Pointer = createRecursivePointer("file:///parent/dir2");
    VirtualFilePointer subdirPointer = createRecursivePointer("file:///parent/dir1/subdir");
    VirtualFilePointer filePointer = createPointer("file:///parent/dir1/subdir/file.txt");
    LightVirtualFile root = new LightVirtualFile("/");
    LightVirtualFile parent = new LightVirtualFileWithParent("parent", root);
    LightVirtualFile dir1 = new LightVirtualFileWithParent("dir1", parent);
    LightVirtualFile dir2 = new LightVirtualFileWithParent("dir2", parent);
    LightVirtualFile subdir = new LightVirtualFileWithParent("subdir", dir1);
    assertPointersUnder(subdir, "xxx.txt", parentPointer, dir1Pointer, subdirPointer);
    assertPointersUnder(subdir, "", parentPointer, dir1Pointer, subdirPointer, filePointer);
    assertPointersUnder(dir1, "", parentPointer, dir1Pointer, subdirPointer, filePointer);
    assertPointersUnder(parent, "", parentPointer, dir1Pointer, dir2Pointer, subdirPointer, filePointer);
    assertPointersUnder(dir2, "", parentPointer, dir2Pointer);
  }

  public void testRecursivePointersUnderSiblingDirectory() {
    VirtualFilePointer innerPointer = createRecursivePointer("file:///parent/dir/subdir1/inner/subinner");
    createPointer("file:///parent/anotherDir");
    LightVirtualFile root = new LightVirtualFile("/");
    LightVirtualFile parent = new LightVirtualFileWithParent("parent", root);
    LightVirtualFile dir = new LightVirtualFileWithParent("dir", parent);
    LightVirtualFile subdir1 = new LightVirtualFileWithParent("subdir1", dir);
    LightVirtualFile subdir2 = new LightVirtualFileWithParent("subdir2", dir);
    assertPointersUnder(subdir1, "inner", innerPointer);
    assertPointersUnder(subdir2, "xxx.txt");
  }

  public void testRecursivePointersUnderDisparateDirectoriesNearRoot() {
    VirtualFilePointer innerPointer = createRecursivePointer("file:///temp/res/ext-resources");
    LightVirtualFile root = new LightVirtualFile("/");
    LightVirtualFile parent = new LightVirtualFileWithParent("parent", root);
    LightVirtualFile dir = new LightVirtualFileWithParent("dir", parent);
    assertPointersUnder(dir, "inner");
    assertTrue(innerPointer.isRecursive());
  }

  public void testUrlsHavingOnlyStartingSlashInCommon() {
    VirtualFilePointer p1 = createPointer("file:///a/p1");
    VirtualFilePointer p2 = createPointer("file:///b/p2");
    LightVirtualFile root = new LightVirtualFile("/");
    LightVirtualFile a = new LightVirtualFileWithParent("a", root);
    LightVirtualFile b = new LightVirtualFileWithParent("b", root);
    assertSameElements(myVirtualFilePointerManager.getPointersUnder(a, "p1"), p1);
    assertSameElements(myVirtualFilePointerManager.getPointersUnder(b, "p2"), p2);
  }

  public void testUrlsHavingOnlyStartingSlashInCommonAndInvalidUrlBetweenThem() {
    VirtualFilePointer p1 = createPointer("file:///a/p1");
    createPointer("file://invalid/path");
    VirtualFilePointer p2 = createPointer("file:///b/p2");
    LightVirtualFile root = new LightVirtualFile("/");
    LightVirtualFile a = new LightVirtualFileWithParent("a", root);
    LightVirtualFile b = new LightVirtualFileWithParent("b", root);
    assertSameElements(myVirtualFilePointerManager.getPointersUnder(a, "p1"), p1);
    assertSameElements(myVirtualFilePointerManager.getPointersUnder(b, "p2"), p2);
  }

  private void assertPointersUnder(@NotNull LightVirtualFile file, @NotNull String childName, @NotNull VirtualFilePointer... pointers) {
    assertSameElements(myVirtualFilePointerManager.getPointersUnder(file, childName), pointers);
  }

  @NotNull
  private VirtualFilePointer createPointer(String url) {
    return myVirtualFilePointerManager.create(url, getTestRootDisposable(), myDummyListener);
  }

  @NotNull
  private VirtualFilePointer createRecursivePointer(String url) {
    return myVirtualFilePointerManager.createDirectoryPointer(url, true, getTestRootDisposable(), myDummyListener);
  }

  private static class LightVirtualFileWithParent extends LightVirtualFile {
    private final LightVirtualFile myParent;

    private LightVirtualFileWithParent(@NotNull String name, @Nullable LightVirtualFile parent) {
      super(name);
      myParent = parent;
    }

    @Override
    public VirtualFile getParent() {
      return myParent;
    }
  }
}
