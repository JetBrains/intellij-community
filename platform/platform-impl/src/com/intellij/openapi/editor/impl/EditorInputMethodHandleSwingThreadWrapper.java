// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodRequests;
import java.text.AttributedCharacterIterator;


final class EditorInputMethodHandleSwingThreadWrapper implements InputMethodRequests {

  private final @NotNull InputMethodRequests delegate;

  EditorInputMethodHandleSwingThreadWrapper(@NotNull InputMethodRequests delegate) {
    this.delegate = delegate;
  }

  @Override
  public @NotNull Rectangle getTextLocation(TextHitInfo offset) {
    return execute(() -> delegate.getTextLocation(offset));
  }

  @Override
  public TextHitInfo getLocationOffset(int x, int y) {
    return execute(() -> delegate.getLocationOffset(x, y));
  }

  @Override
  public int getInsertPositionOffset() {
    return execute(() -> delegate.getInsertPositionOffset());
  }

  @Override
  public @NotNull AttributedCharacterIterator getCommittedText(
    int beginIndex,
    int endIndex,
    AttributedCharacterIterator.Attribute[] attributes
  ) {
    return execute(() -> delegate.getCommittedText(beginIndex, endIndex, attributes));
  }

  @Override
  public int getCommittedTextLength() {
    return execute(delegate::getCommittedTextLength);
  }

  @Override
  public @Nullable AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
    return null;
  }

  @Override
  public AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
    return execute(() -> delegate.getSelectedText(attributes));
  }

  private static <T> T execute(@NotNull ThrowableComputable<T, RuntimeException> computable) {
    return UIUtil.invokeAndWaitIfNeeded(() -> EditorThreading.compute(computable));
  }
}
