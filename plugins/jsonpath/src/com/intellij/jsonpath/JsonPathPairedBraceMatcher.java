// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath;

import com.intellij.jsonpath.psi.JsonPathTypes;
import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class JsonPathPairedBraceMatcher implements PairedBraceMatcher {
  public static final BracePair[] PAIRS = new BracePair[]{
    new BracePair(JsonPathTypes.LPARENTH, JsonPathTypes.RPARENTH, true),
    new BracePair(JsonPathTypes.LBRACKET, JsonPathTypes.RBRACKET, true),
    new BracePair(JsonPathTypes.LBRACE, JsonPathTypes.RBRACE, true)
  };

  @Override
  public BracePair @NotNull [] getPairs() {
    return PAIRS;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
    return true;
  }

  @Override
  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}