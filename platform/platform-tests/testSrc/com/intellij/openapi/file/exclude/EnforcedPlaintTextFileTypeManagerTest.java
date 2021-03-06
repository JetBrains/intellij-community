// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;

/**
 * @author Rustam Vishnyakov
 */
public class EnforcedPlaintTextFileTypeManagerTest extends BasePlatformTestCase {
  public void testMarkAsPlainText() {
    EnforcedPlainTextFileTypeManager manager = EnforcedPlainTextFileTypeManager.getInstance();
    VirtualFile file = myFixture.getTempDirFixture().createFile("test.xml");
    FileType originalType = file.getFileType();
    assertEquals(StdFileTypes.XML, originalType);
    manager.markAsPlainText(getProject(), file);
    UIUtil.dispatchAllInvocationEvents(); // reparseFiles in invokeLater
    FileType changedType = file.getFileType();
    assertEquals(EnforcedPlainTextFileType.INSTANCE, changedType);
    assertTrue(FileTypeManager.getInstance().isFileOfType(file, EnforcedPlainTextFileType.INSTANCE));
    assertFalse(FileTypeManager.getInstance().isFileOfType(file, StdFileTypes.XML));
    manager.resetOriginalFileType(getProject(), file);
    FileType revertedType = file.getFileType();
    assertEquals(originalType, revertedType);
  }
}
