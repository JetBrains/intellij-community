// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.formatter.blocks;

import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;

import java.util.ArrayList;
import java.util.List;

public class MethodCallWithoutQualifierBlock extends GroovyBlock {
  private final boolean myTopLevel;
  private final List<ASTNode> myChildren;

  public MethodCallWithoutQualifierBlock(@NotNull Wrap wrap,
                                         boolean topLevel,
                                         @NotNull List<ASTNode> children,
                                         @NotNull FormattingContext context) {
    super(children.get(0), Indent.getContinuationWithoutFirstIndent(), wrap, context);
    myTopLevel = topLevel;
    myChildren = children;
  }

  @NotNull
  @Override
  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlocks = new ArrayList<>();
      new GroovyBlockGenerator(this).addNestedChildrenSuffix(mySubBlocks, myTopLevel, myChildren);
    }
    return mySubBlocks;
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    return new TextRange(myChildren.get(0).getTextRange().getStartOffset(), myChildren.get(myChildren.size() - 1).getTextRange().getEndOffset());
  }

  @Override
  public boolean isLeaf() {
    return false;
  }
}