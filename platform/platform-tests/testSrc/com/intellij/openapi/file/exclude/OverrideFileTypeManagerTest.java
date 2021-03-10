// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;

public class OverrideFileTypeManagerTest extends BasePlatformTestCase {
  public void testMarkAsPlainText() {
    OverrideFileTypeManager manager = OverrideFileTypeManager.getInstance();
    VirtualFile file = myFixture.getTempDirFixture().createFile("test.xml");
    FileType originalType = file.getFileType();
    assertEquals(StdFileTypes.XML, originalType);
    manager.addFile(file, PlainTextFileType.INSTANCE);
    UIUtil.dispatchAllInvocationEvents(); // reparseFiles in invokeLater
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());
    assertTrue(FileTypeManager.getInstance().isFileOfType(file, PlainTextFileType.INSTANCE));
    assertFalse(FileTypeManager.getInstance().isFileOfType(file, StdFileTypes.XML));
    manager.removeFile(file);
    UIUtil.dispatchAllInvocationEvents(); // reparseFiles in invokeLater
    FileType revertedType = file.getFileType();
    assertEquals(originalType, revertedType);

    manager.addFile(file, ModuleFileType.INSTANCE);
    UIUtil.dispatchAllInvocationEvents(); // reparseFiles in invokeLater
    assertEquals(ModuleFileType.INSTANCE, file.getFileType());
    manager.removeFile(file);
    UIUtil.dispatchAllInvocationEvents(); // reparseFiles in invokeLater
    assertEquals(originalType, file.getFileType());
  }
}
