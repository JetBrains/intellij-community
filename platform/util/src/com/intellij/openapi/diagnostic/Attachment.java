/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.Base64Converter;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;

public class Attachment {
  public static final Attachment[] EMPTY_ARRAY = new Attachment[0];
  private final String myPath;
  private final byte[] myBytes;
  private boolean myIncluded = true;
  private final String myDisplayText;

  public Attachment(@NotNull String path, @NotNull String content) {
    myPath = path;
    myDisplayText = content;
    myBytes = getBytes(content);
  }

  public Attachment(@NotNull String path, @NotNull byte[] bytes, @NotNull String displayText) {
    myPath = path;
    myBytes = bytes;
    myDisplayText = displayText;
  }

  @NotNull
  public static byte[] getBytes(@NotNull String content) {
    return content.getBytes(CharsetToolkit.UTF8_CHARSET);
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
    return Base64Converter.encode(myBytes);
  }

  @NotNull
  public byte[] getBytes() {
    return myBytes;
  }

  public boolean isIncluded() {
    return myIncluded;
  }

  public void setIncluded(boolean included) {
    myIncluded = included;
  }
}
