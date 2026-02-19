// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

@ApiStatus.Internal
public final class CaretId {
  private final String myCaretId;

  public CaretId() {
    myCaretId = UUID.randomUUID().toString();
  }

  @Override
  public int hashCode() {
    return myCaretId.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof CaretId && myCaretId.equals(((CaretId)obj).myCaretId);
  }
}
