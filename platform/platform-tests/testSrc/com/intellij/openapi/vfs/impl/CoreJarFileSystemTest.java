/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ObjectUtils;

/**
 * @author yole
 */
public class CoreJarFileSystemTest extends UsefulTestCase{
  public void testSimple() {
    CoreJarFileSystem fs = new CoreJarFileSystem();
    String jarPath = PlatformTestUtil.getPlatformTestDataPath() + "vfs/corejar/rt.jar";
    VirtualFile root = ObjectUtils.assertNotNull(fs.findFileByPath(jarPath + "!/"));
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
