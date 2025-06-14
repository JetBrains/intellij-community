// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/** Specific tests for VFS roots behavior */
@TestApplication
public class PersistentFS_Roots_Test {


  @Test
  void detectFileSystem_CorrectlyDetectsFileSystems_ForFewCommonlyUsedUrls() {
    assertSame(
      PersistentFSImpl.detectFileSystem("file://Z:"),
      LocalFileSystem.getInstance()
    );
    assertSame(
      PersistentFSImpl.detectFileSystem("file://Z:/"),
      LocalFileSystem.getInstance()
    );

    assertSame(
      PersistentFSImpl.detectFileSystem("file:"),
      LocalFileSystem.getInstance()
    );
    assertSame(
      PersistentFSImpl.detectFileSystem("temp:"),
      TempFileSystem.getInstance()
    );

    assertSame(
      PersistentFSImpl.detectFileSystem("jar://a/b/c.jar!"),
      JarFileSystem.getInstance()
    );
  }

  
  @Test//IDEA-331415
  void detectFileSystem_FailsOnIncorrectUrl() {
    try {
      PersistentFSImpl.detectFileSystem("Z:");
      fail("Windows drive is not a valid URL -> should fail");
    }
    catch (IllegalArgumentException e) {
      //Special case (IDEA-331415): Windows drive is not a valid URL
    }
  }
}
