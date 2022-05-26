// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class OverrideFileTypeManagerTest extends BasePlatformTestCase {
  public void testMarkAsPlainText() {
    OverrideFileTypeManager manager = OverrideFileTypeManager.getInstance();
    VirtualFile xml = myFixture.getTempDirFixture().createFile("test.xml");
    FileType originalType = xml.getFileType();
    assertEquals(StdFileTypes.XML, originalType);
    manager.addFile(xml, PlainTextFileType.INSTANCE);
    UIUtil.dispatchAllInvocationEvents(); // reparseFiles in invokeLater
    assertEquals(PlainTextFileType.INSTANCE, xml.getFileType());
    assertTrue(FileTypeManager.getInstance().isFileOfType(xml, PlainTextFileType.INSTANCE));
    assertFalse(FileTypeManager.getInstance().isFileOfType(xml, StdFileTypes.XML));
    manager.removeFile(xml);
    UIUtil.dispatchAllInvocationEvents(); // reparseFiles in invokeLater
    FileType revertedType = xml.getFileType();
    assertEquals(originalType, revertedType);

    manager.addFile(xml, ArchiveFileType.INSTANCE);
    UIUtil.dispatchAllInvocationEvents(); // reparseFiles in invokeLater
    assertEquals(ArchiveFileType.INSTANCE, xml.getFileType());
    manager.removeFile(xml);
    UIUtil.dispatchAllInvocationEvents(); // reparseFiles in invokeLater
    assertEquals(originalType, xml.getFileType());
  }

  public void testMustNotBeAbleToOverrideNotOverridableFileType() throws IOException {
    OverrideFileTypeManager manager = OverrideFileTypeManager.getInstance();
    VirtualFile dir = myFixture.getTempDirFixture().findOrCreateDir("dir");
    assertThrows(IllegalArgumentException.class, ()->manager.addFile(dir, PlainTextFileType.INSTANCE));
    VirtualFile iml = myFixture.getTempDirFixture().createFile("x.iml", "");
    assertTrue(iml.getFileType().toString(), iml.getFileType() instanceof InternalFileType);
    assertThrows(IllegalArgumentException.class, ()->manager.addFile(iml, PlainTextFileType.INSTANCE));
    VirtualFile text = myFixture.getTempDirFixture().createFile("x.txt", "");
    assertTrue(text.getFileType().toString(), text.getFileType() instanceof PlainTextFileType);
    assertThrows(IllegalArgumentException.class, ()->manager.addFile(text, ProjectFileType.INSTANCE));

    File file = new File(FileUtil.getTempDirectory() + "/x.skjdhfjksdfkjsdhf");
    FileUtil.writeToFile(file, new byte[]{1,0,2,3,4});
    VirtualFile unknown = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertEquals(UnknownFileType.INSTANCE, unknown.getFileType());
    assertThrows(IllegalArgumentException.class, ()->manager.addFile(unknown, PlainTextFileType.INSTANCE));
    assertThrows(IllegalArgumentException.class, ()->manager.addFile(text, UnknownFileType.INSTANCE));

    FileType fakeType = new FakeFileType() {
      @Override public boolean isMyFileType(@NotNull VirtualFile file) { return false; }
      @Override public @NotNull String getName() { return "name"; }
      @Override public @Nls @NotNull String getDisplayName() { return getName(); }
      @Override public @NotNull @NlsContexts.Label String getDescription() { return getName(); }
    };
    assertThrows(IllegalArgumentException.class, ()->manager.addFile(text, fakeType));
  }
}
