// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.formatter.blocks;

import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class MethodCallWithoutQualifierBlock extends GroovyBlock {
  private final PsiElement myNameElement;
  private final boolean myTopLevel;
  private final List<ASTNode> myChildren;
  private final PsiElement myElem;

  public MethodCallWithoutQualifierBlock(PsiElement nameElement,
                                         Wrap wrap,
                                         boolean topLevel,
                                         List<ASTNode> children,
                                         PsiElement elem, FormattingContext context) {
    super(nameElement.getNode(), Indent.getContinuationWithoutFirstIndent(), wrap, context);
    myNameElement = nameElement;
    myTopLevel = topLevel;
    myChildren = children;
    myElem = elem;
  }

  @NotNull
  @Override
  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlocks = new ArrayList<>();
      final Indent indent = Indent.getContinuationWithoutFirstIndent();
      mySubBlocks.add(new GroovyBlock(myNameElement.getNode(), indent, Wrap.createWrap(WrapType.NONE, false), myContext));
      new GroovyBlockGenerator(this).addNestedChildrenSuffix(mySubBlocks, myTopLevel, myChildren.subList(1, myChildren.size()));
    }
    return mySubBlocks;
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    return new TextRange(myNameElement.getTextRange().getStartOffset(), myElem.getTextRange().getEndOffset());
  }

  @Override
  public boolean isLeaf() {
    return false;
  }
}