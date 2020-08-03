// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TemporaryDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;

public class EditorConfigEncodingTest extends EditorConfigFileSettingsTestCase {
  public void testUtf8Bom() throws IOException {
    Path dir = TemporaryDirectory.generateTemporaryPath("dirWithEditorConfig");
    Files.createDirectories(dir);
    Files.copy(getTestDataPath().resolve(".editorconfig"), dir.resolve(".editorconfig"));
    VirtualFile targetDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir);
    VirtualFile newFile = WriteAction.computeAndWait(() -> {
      return targetDir.createChildData(this, "test.txt");
    });
    assertArrayEquals(CharsetToolkit.UTF8_BOM, newFile.getBOM());
  }

  @Override
  protected String getRelativePath() {
    return "plugins/editorconfig/testData/org/editorconfig/configmanagement/encoding";
  }
}