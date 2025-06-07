// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.AttachmentFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentFactoryTest {
  @Test void stringContent() {
    var content = "*".repeat(1024);
    var attachment = new Attachment("pure text.txt", content);
    assertThat(attachment.getDisplayText()).isEqualTo(content);
    assertThat(attachment.getBytes()).isEqualTo(content.getBytes(StandardCharsets.UTF_8));
  }

  @Test void bigFilesStoredOnDisk(@TempDir Path tempDir) throws IOException {
    var testFile = tempDir.resolve("a big one.txt");
    var content = "*".repeat(100 * 1024);
    var contentBytes = content.getBytes(StandardCharsets.UTF_8);
    Files.write(testFile, contentBytes);

    var attachment = AttachmentFactory.createAttachment(testFile, false);
    assertThat(attachment.getDisplayText()).isNotEmpty().isNotEqualTo(content);
    assertThat(attachment.getBytes()).isEqualTo(contentBytes);
    try (var stream = attachment.openContentStream()) {
      assertThat(stream).hasBinaryContent(contentBytes);
    }
  }

  @Test void smallFilesStoredInMemory(@TempDir Path tempDir) throws IOException {
    var testFile = tempDir.resolve("a little one.txt");
    var content = "*".repeat(1024);
    var contentBytes = content.getBytes(StandardCharsets.UTF_8);
    Files.write(testFile, contentBytes);

    var attachment = AttachmentFactory.createAttachment(testFile, false);
    assertThat(attachment.getDisplayText()).isEqualTo(content);
    assertThat(attachment.getBytes()).isEqualTo(contentBytes);
    try (var stream = attachment.openContentStream()) {
      assertThat(stream).hasBinaryContent(contentBytes);
    }
  }
}
