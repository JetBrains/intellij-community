// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.hint;

import com.intellij.codeInsight.hint.DeclarationRangeHandler;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;

public final class GrClassInitializerDeclarationRangeHandler implements DeclarationRangeHandler<GrClassInitializer> {
  @Override
  public @NotNull TextRange getDeclarationRange(@NotNull GrClassInitializer initializer) {
    int startOffset = initializer.getModifierList().getTextRange().getStartOffset();
    int endOffset = initializer.getBlock().getTextRange().getStartOffset();
    return new TextRange(startOffset, endOffset);
  }
}
