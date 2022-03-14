// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.formatter.blocks;

import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroRegistryService;
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroTransformationSupportEx;
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroUtilKt;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
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
      List<Block> macroBlocks = getMacroBlocks(myNode, myChildren, myContext, new GroovyBlockGenerator(this));
      if (macroBlocks == null) {
        new GroovyBlockGenerator(this).addNestedChildrenSuffix(mySubBlocks, myTopLevel, myChildren);
      } else {
        mySubBlocks.addAll(macroBlocks);
      }
    }
    return mySubBlocks;
  }

  private static @Nullable List<Block> getMacroBlocks(ASTNode rootNode, List<ASTNode> children, FormattingContext context,
                                                      GroovyBlockGenerator generator) {
    GrMethodCall psi = PsiTreeUtil.getParentOfType(rootNode.getPsi(), GrMethodCall.class);
    if (psi != null) {
      GroovyMacroRegistryService macroRegistry = psi.getProject().getService(GroovyMacroRegistryService.class);
      PsiMethod macro = macroRegistry.resolveAsMacro(psi);
      if (macro != null) {
        var handlerResult = GroovyMacroUtilKt.getMacroHandler(psi);
        GroovyMacroTransformationSupportEx support = handlerResult != null && handlerResult.getSecond() instanceof GroovyMacroTransformationSupportEx ? ((GroovyMacroTransformationSupportEx)handlerResult.getSecond()) : null;
        List<Block> blocks = new ArrayList<>(3);
        for (ASTNode node : children) {
          if (support != null) {
            blocks.add(support.computeFormattingBlock(psi, node, context, generator));
          } else {
            blocks.add(new GroovyMacroBlock(node, context));
          }
        }
        return blocks;
      }
    }
    return null;
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