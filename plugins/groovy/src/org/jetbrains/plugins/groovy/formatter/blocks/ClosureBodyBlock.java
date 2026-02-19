// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.formatter.blocks;

import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
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
public class ClosureBodyBlock extends GroovyBlock {
  private TextRange myTextRange;

  public ClosureBodyBlock(@NotNull ASTNode node,
                          @NotNull Indent indent,
                          @Nullable Wrap wrap,
                          FormattingContext context) {
    super(node, indent, wrap, context);
  }

  @Override
  public @NotNull TextRange getTextRange() {
    init();
    return myTextRange;
  }

  private void init() {
    if (mySubBlocks == null) {
      GroovyBlockGenerator generator = new GroovyBlockGenerator(this);
      List<ASTNode> children = GroovyBlockGenerator.getClosureBodyVisibleChildren(myNode.getTreeParent());

      mySubBlocks = generator.generateCodeSubBlocks(children);

      //at least -> exists
      assert !mySubBlocks.isEmpty();
      TextRange firstRange = mySubBlocks.get(0).getTextRange();
      TextRange lastRange = mySubBlocks.get(mySubBlocks.size() - 1).getTextRange();
      myTextRange = new TextRange(firstRange.getStartOffset(), lastRange.getEndOffset());
    }
  }

  @Override
  public @NotNull List<Block> getSubBlocks() {
    init();
    return mySubBlocks;
  }

  @Override
  public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
    return new ChildAttributes(Indent.getNormalIndent(), null);
  }

  @Override
  public boolean isIncomplete() {
    return true;
  }
}
