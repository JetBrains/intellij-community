// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * test for building and processing tree of {@link FilePartNode}s.
 */
public class VirtualFilePointersTreeTest extends HeavyPlatformTestCase {
  private VirtualFilePointerManagerImpl myVirtualFilePointerManager;
  private VirtualFile myDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myVirtualFilePointerManager = (VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance();
    myDir = getTempDir().createVirtualDir();
  }

  public void testRecursivePointersForSubdirectories() {
    VirtualFilePointer parentPointer = createRecursivePointer("parent");
    VirtualFilePointer dirPointer = createRecursivePointer("parent/dir");
    VirtualFilePointer subdirPointer = createRecursivePointer("parent/dir/subdir");
    VirtualFilePointer filePointer = createPointer("parent/dir/subdir/file.txt");
    VirtualFile root = myDir;
    VirtualFileSystemEntry parent = (VirtualFileSystemEntry)createChildDirectory(root, "parent");
    VirtualFileSystemEntry dir = (VirtualFileSystemEntry)createChildDirectory(parent, "dir");
    VirtualFileSystemEntry subdir = (VirtualFileSystemEntry)createChildDirectory(dir, "subdir");
    assertPointersUnder(subdir, "xxx.txt", parentPointer, dirPointer, subdirPointer);
    assertPointersUnder(subdir.getParent(), subdir.getName(), parentPointer, dirPointer, subdirPointer, filePointer);
    assertPointersUnder(dir.getParent(), dir.getName(), parentPointer, dirPointer, subdirPointer, filePointer);
    assertPointersUnder(parent.getParent(), parent.getName(), parentPointer, dirPointer, subdirPointer, filePointer);
  }

  public void testRecursivePointersForDirectoriesWithCommonPrefix() {
    VirtualFilePointer parentPointer = createRecursivePointer("parent");
    VirtualFilePointer dir1Pointer = createRecursivePointer("parent/dir1");
    VirtualFilePointer dir2Pointer = createRecursivePointer("parent/dir2");
    VirtualFilePointer subdirPointer = createRecursivePointer("parent/dir1/subdir");
    VirtualFilePointer filePointer = createPointer("parent/dir1/subdir/file.txt");
    VirtualFile root = myDir;
    VirtualFileSystemEntry parent = (VirtualFileSystemEntry)createChildDirectory(root, "parent");
    VirtualFileSystemEntry dir1 = (VirtualFileSystemEntry)createChildDirectory(parent, "dir1");
    VirtualFileSystemEntry dir2 = (VirtualFileSystemEntry)createChildDirectory(parent, "dir2");
    VirtualFileSystemEntry subdir = (VirtualFileSystemEntry)createChildDirectory(dir1, "subdir");
    assertPointersUnder(subdir, "xxx.txt", parentPointer, dir1Pointer, subdirPointer);
    assertPointersUnder(subdir.getParent(), subdir.getName(), parentPointer, dir1Pointer, subdirPointer, filePointer);
    assertPointersUnder(dir1.getParent(), dir1.getName(), parentPointer, dir1Pointer, subdirPointer, filePointer);
    assertPointersUnder(parent.getParent(), parent.getName(), parentPointer, dir1Pointer, dir2Pointer, subdirPointer, filePointer);
    assertPointersUnder(dir2.getParent(), dir2.getName(), parentPointer, dir2Pointer);
  }

  public void testRecursivePointersUnderSiblingDirectory() {
    VirtualFilePointer innerPointer = createRecursivePointer("parent/dir/subdir1/inner/subinner");
    createPointer("parent/anotherDir");
    VirtualFile root = myDir;
    VirtualFile parent = createChildDirectory(root, "parent");
    VirtualFile dir = createChildDirectory(parent, "dir");
    VirtualFileSystemEntry subdir1 = (VirtualFileSystemEntry)createChildDirectory(dir, "subdir1");
    VirtualFileSystemEntry subdir2 = (VirtualFileSystemEntry)createChildDirectory(dir, "subdir2");
    assertPointersUnder(subdir1, "inner", innerPointer);
    assertPointersUnder(subdir2, "xxx.txt");
  }

  public void testRecursivePointersUnderDisparateDirectoriesNearRoot() {
    VirtualFilePointer innerPointer = createRecursivePointer("temp/res/ext-resources");
    VirtualFile root = myDir;
    VirtualFile parent = createChildDirectory(root, "parent");
    VirtualFileSystemEntry dir = (VirtualFileSystemEntry)createChildDirectory(parent, "dir");
    assertPointersUnder(dir, "inner");
    assertTrue(((VirtualFilePointerImpl)innerPointer).isRecursive());
  }

  public void testUrlsHavingOnlyStartingSlashInCommon() {
    VirtualFilePointer p1 = createPointer("a/p1");
    VirtualFilePointer p2 = createPointer("b/p2");
    VirtualFile root = myDir;
    VirtualFileSystemEntry a = (VirtualFileSystemEntry)createChildDirectory(root, "a");
    VirtualFileSystemEntry b = (VirtualFileSystemEntry)createChildDirectory(root, "b");
    assertSameElements(myVirtualFilePointerManager.getPointersUnder(a, "p1"), p1);
    assertSameElements(myVirtualFilePointerManager.getPointersUnder(b, "p2"), p2);
  }

  public void testUrlsHavingOnlyStartingSlashInCommonAndInvalidUrlBetweenThem() {
    VirtualFilePointer p1 = createPointer("a/p1");
    createPointer("invalid/path");
    VirtualFilePointer p2 = createPointer("b/p2");
    VirtualFile root = myDir;
    VirtualFileSystemEntry a = (VirtualFileSystemEntry)createChildDirectory(root, "a");
    VirtualFileSystemEntry b = (VirtualFileSystemEntry)createChildDirectory(root, "b");
    assertSameElements(myVirtualFilePointerManager.getPointersUnder(a, "p1"), p1);
    assertSameElements(myVirtualFilePointerManager.getPointersUnder(b, "p2"), p2);
  }

  private void assertPointersUnder(@NotNull VirtualFileSystemEntry file, @NotNull String childName, VirtualFilePointer @NotNull ... pointers) {
    assertSameElements(myVirtualFilePointerManager.getPointersUnder(file, childName), pointers);
  }

  @NotNull
  private VirtualFilePointer createPointer(String relativePath) {
    return myVirtualFilePointerManager.create(myDir.getUrl()+"/"+relativePath, getTestRootDisposable(), null);
  }

  @NotNull
  private VirtualFilePointer createRecursivePointer(@NotNull String relativePath) {
    return myVirtualFilePointerManager.createDirectoryPointer(myDir.getUrl()+"/"+relativePath, true, getTestRootDisposable(), new VirtualFilePointerListener() {
    });
  }
}
