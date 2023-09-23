// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Specific tests for VFS roots behavior */
@TestApplication
public class PersistentFS_Roots_Test {


  @Test
  void detectFileSystem_CorrectlyDetectsFileSystems_ForFewCommonlyUsedUrls() {
    assertSame(
      PersistentFSImpl.detectFileSystem("Z:", "Z:"),
      LocalFileSystem.getInstance(),
      "Special case: workaround for IDEA-331415 -- likely to be removed after VFS become protected of such roots"
    );
    assertSame(
      PersistentFSImpl.detectFileSystem("file://Z:", "Z:"),
      LocalFileSystem.getInstance()
    );
    assertSame(
      PersistentFSImpl.detectFileSystem("file://Z:/", "Z:/"),
      LocalFileSystem.getInstance()
    );

    assertSame(
      PersistentFSImpl.detectFileSystem("file:", "/"),
      LocalFileSystem.getInstance()
    );
    assertSame(
      PersistentFSImpl.detectFileSystem("temp:", "/"),
      TempFileSystem.getInstance()
    );

    assertSame(
      PersistentFSImpl.detectFileSystem("jar://a/b/c.jar!", "/"),
      JarFileSystem.getInstance()
    );
  }

}
