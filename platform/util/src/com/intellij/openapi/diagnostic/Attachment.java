// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Base64;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

public class Attachment {
  private static final Logger LOG = Logger.getInstance(Attachment.class);

  public static final Attachment[] EMPTY_ARRAY = new Attachment[0];

  private final String myPath;
  private final String myDisplayText;
  private final @Nullable byte[] myBytes;
  private final @Nullable File myTemporaryFile;
  private boolean myIncluded;   // opt-out for traces, opt-in otherwise

  public Attachment(@NotNull String name, @NotNull Throwable throwable) {
    this(name + ".trace", ExceptionUtil.getThrowableText(throwable));
    myIncluded = true;
  }

  public Attachment(@NotNull String path, @NotNull String content) {
    this(path, content, content.getBytes(CharsetToolkit.UTF8_CHARSET), null);
  }

  public Attachment(@NotNull String path, @NotNull byte[] bytes, @NotNull String displayText) {
    this(path, displayText, bytes, null);
  }

  public Attachment(@NotNull String path, @NotNull File temporaryFile, @NotNull String displayText) {
    this(path, displayText, null, temporaryFile);
  }

  private Attachment(String path, String displayText, @Nullable byte[] bytes, @Nullable File temporaryFile) {
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
  public String getPath() {
    return myPath;
  }

  @NotNull
  public String getName() {
    return PathUtilRt.getFileName(myPath);
  }

  @NotNull
  public String getEncodedBytes() {
    return Base64.encode(getBytes());
  }

  @NotNull
  public byte[] getBytes() {
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

    return ArrayUtil.EMPTY_BYTE_ARRAY;
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

    return new ByteArrayInputStream(ArrayUtil.EMPTY_BYTE_ARRAY);
  }

  public boolean isIncluded() {
    return myIncluded;
  }

  public void setIncluded(boolean included) {
    myIncluded = included;
  }
}