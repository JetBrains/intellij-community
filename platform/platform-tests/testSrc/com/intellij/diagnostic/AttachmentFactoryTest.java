// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class AttachmentFactoryTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  public void testBigFilesStoredOnDisk() throws IOException {
    Path testFile = tempDir.newFile("a big one.txt").toPath();
    String content = StringUtil.repeat("*", 100 * 1024);
    byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
    FileUtil.writeToFile(testFile.toFile(), contentBytes);

    Attachment attachment = AttachmentFactory.createAttachment(testFile, false);
    assertThat(attachment.getDisplayText()).isNotEmpty().isNotEqualTo(content);
    assertThat(attachment.getBytes()).isEqualTo(contentBytes);
    assertThat(FileUtil.loadBytes(attachment.openContentStream())).isEqualTo(contentBytes);
  }

  @Test
  public void testSmallFilesStoredInMemory() throws IOException {
    Path testFile = tempDir.newFile("a little one.txt").toPath();
    String content = StringUtil.repeat("*", 1024);
    byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
    FileUtil.writeToFile(testFile.toFile(), contentBytes);

    Attachment attachment = AttachmentFactory.createAttachment(testFile, false);
    assertThat(attachment.getDisplayText()).isEqualTo(content);
    assertThat(attachment.getBytes()).isEqualTo(contentBytes);
    assertThat(FileUtil.loadBytes(attachment.openContentStream())).isEqualTo(contentBytes);
  }
}