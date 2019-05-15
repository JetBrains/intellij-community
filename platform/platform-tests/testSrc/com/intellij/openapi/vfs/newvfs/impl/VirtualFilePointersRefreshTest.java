// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

@SuppressWarnings("SuspiciousPackagePrivateAccess")
public class VirtualFilePointersRefreshTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  public void testFindChildMustIncreaseModificationCountIfFoundNewFile() throws IOException {
    File dir = tempDir.newFolder("x/dir");
    File xTxt = new File(dir.getParentFile(), "x.txt");

    VirtualFile vX = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir).getParent();
    assertFalse(((VirtualDirectoryImpl)vX).allChildrenLoaded());

    VirtualFilePointer pointerX = VirtualFilePointerManager.getInstance().create(VfsUtilCore.pathToUrl(xTxt.getPath()), getTestRootDisposable(), null);
    assertFalse(pointerX.isValid());

    ((VirtualDirectoryImpl)vX).doClearAdoptedNames();

    assertTrue(xTxt.createNewFile());

    VirtualFile vXTxt = vX.findChild(xTxt.getName());
    assertNotNull(vXTxt);
    assertTrue(vXTxt.isValid());

    // even when child "x.txt" found and created without any events,
    // virtual pointer manager needs to be able to tell some pointers've changed
    assertTrue(pointerX.isValid());
  }
}