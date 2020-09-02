// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Attachment {
  private static final Logger LOG = Logger.getInstance(Attachment.class);

  public static final Attachment[] EMPTY_ARRAY = new Attachment[0];

  private final String myPath;
  private final String myDisplayText;
  private final byte @Nullable [] myBytes;
  private final @Nullable File myTemporaryFile;
  private boolean myIncluded;   // opt-out for traces, opt-in otherwise

  public Attachment(@NotNull @NonNls String name, @NotNull Throwable throwable) {
    this(name + ".trace", ExceptionUtil.getThrowableText(throwable));
    myIncluded = true;
  }

  public Attachment(@NotNull @NonNls String path, @NotNull @NonNls String content) {
    this(path, content, content.getBytes(StandardCharsets.UTF_8), null);
  }

  public Attachment(@NotNull @NonNls String path, byte @NotNull [] bytes, @NotNull @NonNls String displayText) {
    this(path, displayText, bytes, null);
  }

  public Attachment(@NotNull @NonNls String path, @NotNull File temporaryFile, @NotNull @NonNls String displayText) {
    this(path, displayText, null, temporaryFile);
  }

  private Attachment(String path, String displayText, byte @Nullable [] bytes, @Nullable File temporaryFile) {
    assert bytes != null || temporaryFile != null;
    myPath = path;
    myDisplayText = displayText;
    myBytes = bytes;
    myTemporaryFile = temporaryFile;
  }

  @NotNull
  public String getDisplayText() {
    return myDisplayText;
  }

  @NotNull
  @NlsSafe
  public String getPath() {
    return myPath;
  }

  @NotNull
  @NlsSafe
  public String getName() {
    return PathUtilRt.getFileName(myPath);
  }

  @NotNull
  public String getEncodedBytes() {
    return Base64.getEncoder().encodeToString(getBytes());
  }

  public byte @NotNull [] getBytes() {
    if (myBytes != null) {
      return myBytes;
    }

    if (myTemporaryFile != null) {
      try {
        return FileUtil.loadFileBytes(myTemporaryFile);
      }
      catch (IOException e) {
        LOG.error("Failed to read attachment content from temp. file " + myTemporaryFile, e);
      }
    }

    return ArrayUtilRt.EMPTY_BYTE_ARRAY;
  }

  @NotNull
  public InputStream openContentStream() {
    if (myBytes != null) {
      return new ByteArrayInputStream(myBytes);
    }

    if (myTemporaryFile != null) {
      try {
        return new FileInputStream(myTemporaryFile);
      }
      catch (FileNotFoundException e) {
        LOG.error("Failed to read attachment content from temp. file " + myTemporaryFile, e);
      }
    }

    return new ByteArrayInputStream(ArrayUtilRt.EMPTY_BYTE_ARRAY);
  }

  public boolean isIncluded() {
    return myIncluded;
  }

  public void setIncluded(boolean included) {
    myIncluded = included;
  }
}