// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem;
import com.intellij.testFramework.PlatformTestUtil;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author yole
 */
public class CoreJarFileSystemTest {
  @Test
  public void testSimple() {
    String jarPath = PlatformTestUtil.getPlatformTestDataPath() + "vfs/corejar/rt.jar!/";
    VirtualFile root = new CoreJarFileSystem().findFileByPath(jarPath);
    assertNotNull(jarPath, root);
    VirtualFile[] children = root.getChildren();
    assertEquals(4, children.length);

    VirtualFile com = root.findFileByRelativePath("com");
    assertNotNull(com);
    assertEquals(0, com.getChildren().length);
    assertTrue(com.isDirectory());

    VirtualFile arrayList = root.findFileByRelativePath("java/util/ArrayList.class");
    assertNotNull(arrayList);
    assertEquals(0, arrayList.getChildren().length);
    assertFalse(arrayList.isDirectory());
  }
}