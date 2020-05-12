// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;

public class EditorConfigEncodingTest extends EditorConfigFileSettingsTestCase {

  public void testUtf8Bom() throws IOException {
    VirtualFile editorConfig = getVirtualFile(".editorconfig");
    @SuppressWarnings("deprecation") VirtualFile targetDir = getProject().getBaseDir();
    VirtualFile newFile = ApplicationManager.getApplication().runWriteAction(
      new ThrowableComputable<VirtualFile, IOException>() {
        @Override
        public VirtualFile compute() throws IOException {
          VfsUtilCore.copyFile(this, editorConfig, targetDir);
          return targetDir.createChildData(this, "test.txt");
        }
      });
    assertArrayEquals(CharsetToolkit.UTF8_BOM, newFile.getBOM());
  }

  @Override
  protected String getRelativePath() {
    return "plugins/editorconfig/testData/org/editorconfig/configmanagement/encoding";
  }
}