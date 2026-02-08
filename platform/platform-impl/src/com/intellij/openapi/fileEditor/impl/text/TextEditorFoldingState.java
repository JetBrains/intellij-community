// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text;


import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;


final class TextEditorFoldingState {
  private @Nullable CodeFoldingState foldingState;
  private @Nullable Supplier<? extends CodeFoldingState> lazyFoldingState;

  TextEditorFoldingState(@Nullable CodeFoldingState foldingState, @Nullable Supplier<? extends CodeFoldingState> lazyFoldingState) {
    this.foldingState = foldingState;
    this.lazyFoldingState = lazyFoldingState;
  }

  @Nullable
  CodeFoldingState getFoldingState() {
    // Assuming single-thread access here.
    if (foldingState == null && lazyFoldingState != null) {
      foldingState = lazyFoldingState.get();
      if (foldingState != null) {
        lazyFoldingState = null;
      }
    }
    return foldingState;
  }

  @Nullable Supplier<? extends CodeFoldingState> getLazyFoldingState() {
    return lazyFoldingState;
  }
}
