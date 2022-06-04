// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.testFramework.TemporaryDirectory;
import org.editorconfig.Utils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;

public class EditorConfigEncodingTest extends EditorConfigFileSettingsTestCase {
  public void testUtf8Bom() throws IOException {
    VirtualFile newFile = createTargetFile();
    assertArrayEquals(CharsetToolkit.UTF8_BOM, newFile.getBOM());
  }

  public void testOverridden() throws IOException {
    VirtualFile newFile = createTargetFile();
    Charset charset = EncodingManager.getInstance().getEncoding(newFile, true);
    assertEquals(StandardCharsets.ISO_8859_1, charset);
  }

  public void testForcedUtf8() throws IOException {
    VirtualFile newFile = createTargetFile();
    VirtualFile dir = newFile.getParent();
    VirtualFile editorConfig = dir.findChild(Utils.EDITOR_CONFIG_FILE_NAME);
    assertNotNull(editorConfig);
    Charset charset = editorConfig.getCharset();
    assertEquals(StandardCharsets.UTF_8, charset);
  }


  @NotNull
  private VirtualFile createTargetFile() throws IOException {
    Path dir = TemporaryDirectory.generateTemporaryPath(getTestName(true));
    Files.createDirectories(dir);
    Files.copy(getTestDataPath().resolve(".editorconfig"), dir.resolve(".editorconfig"));
    VirtualFile targetDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir);
    VirtualFile file = WriteAction.computeAndWait(() -> {
      return targetDir.createChildData(this, "test.txt");
    });
    EditorConfigEncodingCache.getInstance().computeAndCacheEncoding(getProject(), file);
    return file;
  }

  @Override
  protected String getRelativePath() {
    return "plugins/editorconfig/testData/org/editorconfig/configmanagement/encoding";
  }
}