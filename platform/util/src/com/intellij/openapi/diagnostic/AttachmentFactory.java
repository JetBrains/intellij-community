// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AttachmentFactory {
  private static final Logger LOG = Logger.getInstance(AttachmentFactory.class);
  private static final long BIG_FILE_THRESHOLD_BYTES = 50 * 1024;

  public static @NotNull Attachment createAttachment(@NotNull Path file, boolean isBinary) {
    try (InputStream inputStream = Files.newInputStream(file)) {
      return createAttachment(file.toString(), inputStream, Files.size(file), isBinary);
    }
    catch (IOException e) {
      LOG.warn("failed to create an attachment from " + file, e);
      return new Attachment(file.toString(), e);
    }
  }

  public static Attachment createAttachment(String path, InputStream content, long contentLength, boolean isBinary) throws IOException {
    if (contentLength >= BIG_FILE_THRESHOLD_BYTES) {
      Path tempFile = FileUtil.createTempFile("ij-attachment-" + PathUtilRt.getFileName(path) + '.', isBinary ? ".bin" : ".txt", true).toPath();
      Files.copy(content, tempFile, StandardCopyOption.REPLACE_EXISTING);
      return new Attachment(path, tempFile, "[File is too big to display]");
    }
    else {
      byte[] bytes = StreamUtil.readBytes(content);
      String displayText = isBinary ? "[File is binary]" : new String(bytes, StandardCharsets.UTF_8);
      return new Attachment(path, bytes, displayText);
    }
  }

  public static @NotNull Attachment createContext(@NotNull @NonNls String context) {
    return new Attachment("current-context.txt", !context.isEmpty() ? context : "(unknown)");
  }
}
