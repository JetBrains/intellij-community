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
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
* @author peter
*/
public class MethodCallWithoutQualifierBlock extends GroovyBlock {
  private final PsiElement myNameElement;
  private final Alignment myAlignment;
  private final Wrap myWrap;
  private final CodeStyleSettings mySettings;
  private final boolean myTopLevel;
  private final List<ASTNode> myChildren;
  private final PsiElement myElem;

  public MethodCallWithoutQualifierBlock(PsiElement nameElement,
                                         Alignment alignment,
                                         Wrap wrap,
                                         CodeStyleSettings settings,
                                         boolean topLevel,
                                         List<ASTNode> children, PsiElement elem) {
    super(nameElement.getNode(), alignment, Indent.getContinuationWithoutFirstIndent(), wrap, settings);
    myNameElement = nameElement;
    myAlignment = alignment;
    myWrap = wrap;
    mySettings = settings;
    myTopLevel = topLevel;
    myChildren = children;
    myElem = elem;
  }

  @NotNull
  @Override
  public List<Block> getSubBlocks() {
    ArrayList<Block> blocks = new ArrayList<Block>();
    blocks.add(new GroovyBlock(myNameElement.getNode(), myAlignment, Indent.getContinuationWithoutFirstIndent(), myWrap, mySettings));
    GroovyBlockGenerator.addNestedChildrenSuffix(blocks, myAlignment, myWrap, mySettings, myTopLevel, myChildren, myChildren.size());
    return blocks;
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    return new TextRange(myNameElement.getTextRange().getStartOffset(), myElem.getTextRange().getEndOffset());
  }
}
