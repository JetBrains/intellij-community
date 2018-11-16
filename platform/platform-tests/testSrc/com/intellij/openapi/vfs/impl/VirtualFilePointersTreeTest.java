// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
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

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myVirtualFilePointerManager = (VirtualFilePointerManagerImpl)VirtualFilePointerManager.getInstance();
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

  @NotNull
  private VirtualFilePointer createPointer(String url) {
    return myVirtualFilePointerManager.create(url, getTestRootDisposable(), null);
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
