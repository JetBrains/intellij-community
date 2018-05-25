/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  @Nullable private final File myTemporaryFile;
  @Nullable private final byte[] myBytes;
  private boolean myIncluded;   // opt-out for traces, opt-in otherwise
  private final String myDisplayText;

  public Attachment(@NotNull String path, @NotNull String content) {
    this(path, content.getBytes(CharsetToolkit.UTF8_CHARSET), content);
  }

  public Attachment(@NotNull String path, @NotNull byte[] bytes, @NotNull String displayText) {
    myPath = path;
    myDisplayText = displayText;
    myBytes = bytes;
    myTemporaryFile = null;
  }

  public Attachment(@NotNull String path, @NotNull InputStream inputStream, @NotNull String displayText) {
    myPath = path;
    myDisplayText = displayText;

    myBytes = null;

    File temporaryFile;
    try {
      temporaryFile = FileUtil.createTempFile("intellij-attachment", ".bin", true);
      temporaryFile.deleteOnExit();
    } catch (IOException e) {
      LOG.error("Unable to create temp file for attachment: " + e.getMessage(), e);
      temporaryFile = null;
    }

    if (temporaryFile != null) {
      try {
        OutputStream outputStream = new FileOutputStream(temporaryFile);
        try {
          FileUtil.copy(inputStream, outputStream);
        } finally {
          outputStream.close();
        }
      } catch (IOException e) {
        LOG.error("Unable to write temp file for attachment at " + temporaryFile + ": " + e.getMessage(), e);
        temporaryFile = null;
      }
    }

    myTemporaryFile = temporaryFile;
  }

  public Attachment(@NotNull String path, @NotNull File existingTemporaryFile, @NotNull String displayText) {
    myPath = path;
    myDisplayText = displayText;
    myTemporaryFile = existingTemporaryFile;
    myBytes = null;
  }

  public Attachment(@NotNull String name, @NotNull Throwable throwable) {
    this(name + ".trace", ExceptionUtil.getThrowableText(throwable));
    myIncluded = true;
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

    if (myTemporaryFile == null) {
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }

    try {
      return FileUtil.loadFileBytes(myTemporaryFile);
    } catch (IOException e) {
      LOG.error("Unable to read attachment content from temporary file " + myTemporaryFile + ": " + e.getMessage(), e);
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }
  }

  @NotNull
  public InputStream openContentStream() {
    if (myBytes != null) {
      return new ByteArrayInputStream(myBytes);
    }

    if (myTemporaryFile == null) {
      return new ByteArrayInputStream(ArrayUtil.EMPTY_BYTE_ARRAY);
    }

    try {
      return new FileInputStream(myTemporaryFile);
    } catch (FileNotFoundException e) {
      LOG.warn("Unable to read attachment content from temporary file " + myTemporaryFile + ": " + e.getMessage(), e);
      return new ByteArrayInputStream(ArrayUtil.EMPTY_BYTE_ARRAY);
    }
  }

  public boolean isIncluded() {
    return myIncluded;
  }

  public void setIncluded(boolean included) {
    myIncluded = included;
  }
}
