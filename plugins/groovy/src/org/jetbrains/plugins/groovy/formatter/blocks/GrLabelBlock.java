// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.formatter.blocks;

import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;

import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrLabelBlock extends GroovyBlockWithRange {
  private final List<Block> myBlocks;

  public GrLabelBlock(@NotNull ASTNode node,
                      List<ASTNode> subStatements,
                      @NotNull Indent indent,
                      @Nullable Wrap wrap,
                      @NotNull FormattingContext context) {
    super(node, indent, createTextRange(subStatements), wrap, context);

    final GroovyBlockGenerator generator = new GroovyBlockGenerator(this);
    myBlocks = generator.generateSubBlocks(subStatements);
  }

  private static TextRange createTextRange(List<ASTNode> subStatements) {
    ASTNode first = subStatements.get(0);
    ASTNode last = subStatements.get(subStatements.size() - 1);
    return new TextRange(first.getTextRange().getStartOffset(), last.getTextRange().getEndOffset());
  }

  @NotNull
  @Override
  public List<Block> getSubBlocks() {
    return myBlocks;
  }
}
