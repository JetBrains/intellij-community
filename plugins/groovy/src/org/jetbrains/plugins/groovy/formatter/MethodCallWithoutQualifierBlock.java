/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.formatter;

import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class MethodCallWithoutQualifierBlock extends GroovyBlock {
  private final TextRange myRange;

  protected MethodCallWithoutQualifierBlock(PsiElement nameElement,
                                            Alignment alignment,
                                            Wrap wrap,
                                            CommonCodeStyleSettings settings,
                                            GroovyCodeStyleSettings groovySettings,
                                            boolean topLevel,
                                            List<ASTNode> children,
                                            PsiElement elem,
                                            Map<PsiElement, Alignment> innerAlignments, Map<PsiElement, GroovyBlock> blocks) {
    super(nameElement.getNode(), alignment, Indent.getContinuationWithoutFirstIndent(), wrap, settings, groovySettings, innerAlignments,blocks);
    myRange = new TextRange(nameElement.getTextRange().getStartOffset(), elem.getTextRange().getEndOffset());

    mySubBlocks = new ArrayList<Block>();
    final GroovySimpleBlock first =
      new GroovySimpleBlock(nameElement.getNode(), myInnerAlignments.get(nameElement), Indent.getContinuationWithoutFirstIndent(), myWrap,
                            mySettings, myGroovySettings, myInnerAlignments, myBlocks);
    mySubBlocks.add(first);
    new GroovyBlockGenerator(this).addNestedChildrenSuffix(mySubBlocks, myAlignment, topLevel, children, children.size());
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    return myRange;
  }

  @Override
  public boolean isLeaf() {
    return false;
  }
}
