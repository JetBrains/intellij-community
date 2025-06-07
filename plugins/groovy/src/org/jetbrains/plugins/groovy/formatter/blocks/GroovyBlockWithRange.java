// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.formatter.blocks;

import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;

public class GroovyBlockWithRange extends GroovyBlock {

  private final @NotNull TextRange myTextRange;

  public GroovyBlockWithRange(@NotNull ASTNode node,
                              @NotNull Indent indent,
                              @NotNull TextRange range,
                              @Nullable Wrap wrap,
                              @NotNull FormattingContext context) {
    super(node, indent, wrap, context);

    myTextRange = range;
  }

  @Override
  public @NotNull TextRange getTextRange() {
    return myTextRange;
  }
}
