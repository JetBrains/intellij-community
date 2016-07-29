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
package com.intellij.openapi.diff.impl.patch;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BinaryFilePatch extends FilePatch {
  private final byte[] myBeforeContent;
  private final byte[] myAfterContent;

  public BinaryFilePatch(final byte[] beforeContent, final byte[] afterContent) {
    myBeforeContent = beforeContent;
    myAfterContent = afterContent;
  }

  public boolean isNewFile() {
    return myBeforeContent == null;
  }

  public boolean isDeletedFile() {
    return myAfterContent == null;
  }

  @Nullable
  public byte[] getBeforeContent() {
    return myBeforeContent;
  }

  @Nullable
  public byte[] getAfterContent() {
    return myAfterContent;
  }

  @NotNull
  public BinaryFilePatch copy() {
    BinaryFilePatch copied = new BinaryFilePatch(this.getBeforeContent(), this.getAfterContent());
    copied.setBeforeName(this.getBeforeName());
    copied.setAfterName(this.getAfterName());
    return copied;
  }
}
