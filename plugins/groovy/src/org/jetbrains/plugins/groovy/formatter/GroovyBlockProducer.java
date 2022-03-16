// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlock;

@FunctionalInterface
public interface GroovyBlockProducer {

  GroovyBlockProducer DEFAULT = (node, indent, wrap, context) -> {
    return new GroovyBlock(node, indent, wrap, context);
  };

  Block generateBlock(@NotNull final ASTNode node,
                      @NotNull final Indent indent,
                      @Nullable final Wrap wrap,
                      @NotNull FormattingContext context);
}
