/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.formatter.blocks;

import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiErrorElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.AlignmentProvider;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;
import org.jetbrains.plugins.groovy.formatter.processors.GroovyWrappingProcessor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class ParameterListBlock extends GroovyBlock {
  private final List<Block> mySubBlocks;
  private final TextRange myTextRange;

  @NotNull
  @Override
  public TextRange getTextRange() {
    return myTextRange;
  }

  public ParameterListBlock(@NotNull GrMethod method, @NotNull Indent indent, @Nullable Wrap wrap, @NotNull FormattingContext context) {
    super(method.getParameterList().getNode(), indent, wrap, context);
    final ASTNode methodNode = method.getNode();
    final ASTNode leftParenth = methodNode.findChildByType(GroovyTokenTypes.mLPAREN);
    final ASTNode rightParenth = methodNode.findChildByType(GroovyTokenTypes.mRPAREN);

    final GroovyWrappingProcessor wrappingProcessor = new GroovyWrappingProcessor(this);
    mySubBlocks = new ArrayList<>();
    if (leftParenth != null) {
      mySubBlocks.add(new GroovyBlock(leftParenth, Indent.getNoneIndent(), Wrap.createWrap(WrapType.NONE, false), myContext));
    }

    List<ASTNode> astNodes = GroovyBlockGenerator.visibleChildren(myNode);

    final boolean unfinished = isParameterListUnfinished(myNode);

    if (myContext.getSettings().ALIGN_MULTILINE_PARAMETERS) {
      final AlignmentProvider.Aligner aligner = myContext.getAlignmentProvider().createAligner(false);
      for (ASTNode node : astNodes) {
        aligner.append(node.getPsi());
      }

      if (rightParenth != null && unfinished) {
        aligner.append(rightParenth.getPsi());
      }
    }


    for (ASTNode childNode : astNodes) {
      mySubBlocks.add(new GroovyBlock(childNode, Indent.getContinuationIndent(), wrappingProcessor.getChildWrap(childNode), myContext));
    }


    if (rightParenth != null) {
      mySubBlocks.add(new GroovyBlock(rightParenth,
                                      unfinished ? Indent.getContinuationIndent() : Indent.getNoneIndent(),
                                      Wrap.createWrap(WrapType.NONE, false),
                                      myContext));

      if (!unfinished && myContext.getSettings().ALIGN_MULTILINE_METHOD_BRACKETS) {
        if (leftParenth != null) {
          myContext.getAlignmentProvider().addPair(leftParenth, rightParenth, false);
        }
      }
    }

    myTextRange = TextRange.create(mySubBlocks.get(0).getTextRange().getStartOffset(),
                                   mySubBlocks.get(mySubBlocks.size() - 1).getTextRange().getEndOffset());
  }

  @NotNull
  @Override
  public List<Block> getSubBlocks() {
    return mySubBlocks;
  }

  private static boolean isParameterListUnfinished(ASTNode parameterList) {
    final ASTNode last = parameterList.getLastChildNode();
    return last instanceof PsiErrorElement;
  }
}
