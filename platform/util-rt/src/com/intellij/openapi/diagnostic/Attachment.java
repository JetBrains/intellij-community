// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * @see com.intellij.openapi.diagnostic.AttachmentFactory
 */
public final class Attachment {
  private static final LoggerRt LOG = LoggerRt.getInstance(Attachment.class);

  public static final Attachment[] EMPTY_ARRAY = new Attachment[0];
  private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

  private final String myPath;
  private final String myDisplayText;
  private final byte @Nullable [] myBytes;
  private final @Nullable Path myTemporaryFile;
  private boolean myIncluded;   // opt-out for traces, opt-in otherwise

  public Attachment(@NotNull String name, @NotNull Throwable throwable) {
    this(name + ".trace", getThrowableText(throwable));
    myIncluded = true;
  }

  public Attachment(@NotNull String path, @NotNull String content) {
    this(path, content, null, null);
  }

  public Attachment(@NotNull String path, byte @NotNull [] bytes, @NotNull String displayText) {
    this(path, displayText, bytes, null);
  }

  public Attachment(@NotNull String path, @NotNull Path temporaryFile, @NotNull String displayText) {
    this(path, displayText, null, temporaryFile);
  }

  public Attachment(@NotNull String path, @NotNull File temporaryFile, @NotNull String displayText) {
    this(path, displayText, null, temporaryFile.toPath());
  }

  private Attachment(String path, String displayText, byte @Nullable [] bytes, @Nullable Path temporaryFile) {
    myPath = path;
    myDisplayText = displayText;
    myBytes = bytes;
    myTemporaryFile = temporaryFile;
  }

  public @NotNull String getDisplayText() {
    return myDisplayText;
  }

  public @NotNull String getPath() {
    return myPath;
  }

  public @NotNull String getName() {
    return PathUtilRt.getFileName(myPath);
  }

  public @NotNull String getEncodedBytes() {
    return Base64.getEncoder().encodeToString(getBytes());
  }

  public byte @NotNull [] getBytes() {
    if (myBytes != null) {
      return myBytes;
    }

    if (myTemporaryFile != null) {
      try {
        return Files.readAllBytes(myTemporaryFile);
      }
      catch (IOException e) {
        LOG.error("Failed to read attachment content from temp. file " + myTemporaryFile, e);
      }
    }

    if (myDisplayText != null) {
      return myDisplayText.getBytes(StandardCharsets.UTF_8);
    }

    return EMPTY_BYTE_ARRAY;
  }

  public @NotNull InputStream openContentStream() {
    if (myBytes != null) {
      return new ByteArrayInputStream(myBytes);
    }

    if (myTemporaryFile != null) {
      try {
        return Files.newInputStream(myTemporaryFile);
      }
      catch (IOException e) {
        LOG.error("Failed to read attachment content from temp. file " + myTemporaryFile, e);
      }
    }

    if (myDisplayText != null) {
      return new ByteArrayInputStream(myDisplayText.getBytes(StandardCharsets.UTF_8));
    }

    return new ByteArrayInputStream(EMPTY_BYTE_ARRAY);
  }

  public boolean isIncluded() {
    return myIncluded;
  }

  public void setIncluded(boolean included) {
    myIncluded = included;
  }

  @Override
  public String toString() {
    return "Attachment[" + myPath + "][" + getBytes().length + " bytes]";
  }

  private static @NotNull String getThrowableText(@NotNull Throwable throwable) {
    StringWriter writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }
}
