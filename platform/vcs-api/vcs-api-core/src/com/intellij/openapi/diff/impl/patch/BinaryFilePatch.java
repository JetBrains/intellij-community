// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public boolean isNewFile() {
    return myBeforeContent == null;
  }

  @Override
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
