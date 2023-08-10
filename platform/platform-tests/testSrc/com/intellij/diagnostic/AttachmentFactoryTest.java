// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.AttachmentFactory;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class AttachmentFactoryTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  public void stringContent() {
    String content = "*".repeat(1024);
    Attachment attachment = new Attachment("pure text.txt", content);
    assertThat(attachment.getDisplayText()).isEqualTo(content);
    assertThat(attachment.getBytes()).isEqualTo(content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void bigFilesStoredOnDisk() throws IOException {
    Path testFile = tempDir.newFile("a big one.txt").toPath();
    String content = "*".repeat(100 * 1024);
    byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
    Files.write(testFile, contentBytes);

    Attachment attachment = com.intellij.openapi.diagnostic.AttachmentFactory.createAttachment(testFile, false);
    assertThat(attachment.getDisplayText()).isNotEmpty().isNotEqualTo(content);
    assertThat(attachment.getBytes()).isEqualTo(contentBytes);
    try (InputStream stream = attachment.openContentStream()) {
      assertThat(stream).hasBinaryContent(contentBytes);
    }
  }

  @Test
  public void smallFilesStoredInMemory() throws IOException {
    Path testFile = tempDir.newFile("a little one.txt").toPath();
    String content = "*".repeat(1024);
    byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
    Files.write(testFile, contentBytes);

    Attachment attachment = AttachmentFactory.createAttachment(testFile, false);
    assertThat(attachment.getDisplayText()).isEqualTo(content);
    assertThat(attachment.getBytes()).isEqualTo(contentBytes);
    try (InputStream stream = attachment.openContentStream()) {
      assertThat(stream).hasBinaryContent(contentBytes);
    }
  }
}
