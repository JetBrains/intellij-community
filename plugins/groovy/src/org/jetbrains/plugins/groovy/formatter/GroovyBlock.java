/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.formatting.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.lang.ASTNode;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.formatter.models.BlockedIndent;
import org.jetbrains.plugins.groovy.formatter.processors.GroovyIndentProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

/**
 * Black implementation for Groovy formatter
 *
 * @author Ilya.Sergey
 */
public class GroovyBlock implements Block, GroovyElementTypes {

  final private ASTNode myNode;
  final private Alignment myAlignment;
  final private Indent myIndent;
  private Wrap myWrap;
  final private CodeStyleSettings mySettings;

  private List<Block> mySubBlocks = null;

  public GroovyBlock(@NotNull final ASTNode node, @Nullable final Alignment alignment, @NotNull final Indent indent, @Nullable final Wrap wrap, final CodeStyleSettings settings) {
    myNode = node;
    myAlignment = alignment;
    myIndent = indent;
    myWrap = wrap;
    mySettings = settings;
  }

  @NotNull
  public ASTNode getNode() {
    return myNode;
  }

  @NotNull
  public CodeStyleSettings getSettings() {
    return mySettings;
  }

  @NotNull
  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  @NotNull
  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlocks = generateSubBlocks();
    }
    return mySubBlocks;
  }

  @Nullable
  public Wrap getWrap() {
    return myWrap;
  }

  @Nullable
  public Indent getIndent() {
    return myIndent;
  }

  @Nullable
  public Alignment getAlignment() {
    return myAlignment;
  }

  /**
   * Returns spacing between neighrbour elements
   *
   * @param child1 left element
   * @param child2 right element
   * @return
   */
  @Nullable
  public Spacing getSpacing(Block child1, Block child2) {
    return null;
  }

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    final PsiElement psiParent = getNode().getPsi();
    if (psiParent instanceof GroovyFile) {
      return new ChildAttributes(Indent.getNoneIndent(), null);
    }
    if (psiParent instanceof BlockedIndent) {
      return new ChildAttributes(Indent.getNormalIndent(), null);
    }
    return new ChildAttributes(Indent.getNoneIndent(), null);
  }


  public boolean isIncomplete() {
    return isIncomplete(myNode);
  }

  /**
   * @param node Tree node
   * @return true if node is incomplete
   */
  public boolean isIncomplete(@NotNull final ASTNode node) {
    ASTNode lastChild = node.getLastChildNode();
    while (lastChild != null &&
            (lastChild.getPsi() instanceof PsiWhiteSpace || lastChild.getPsi() instanceof PsiComment)) {
      lastChild = lastChild.getTreePrev();
    }
    if (lastChild == null) {
      return false;
    }
    if (lastChild.getPsi() instanceof PsiErrorElement) {
      return true;
    }
    return isIncomplete(lastChild);
  }

  public boolean isLeaf() {
    return myNode.getFirstChildNode() == null;
  }

  /**
   * @param node Tree node
   * @return true, if the current node can be block node, else otherwise
   */
  private static boolean canBeCorrectBlock(@NotNull final ASTNode node) {
    return (node.getText().trim().length() > 0);
  }

  private List<Block> generateSubBlocks() {
    final ArrayList<Block> subBlocks = new ArrayList<Block>();
    ASTNode children[] = myNode.getChildren(null);
    int childNumber = children.length;
    if (childNumber == 0) {
      return subBlocks;
    }
    ASTNode prevChildNode = null;
    for (ASTNode childNode : children) {
      if (canBeCorrectBlock(childNode)) {
        final Indent indent = GroovyIndentProcessor.getChildIndent(this, prevChildNode, childNode);
        subBlocks.add(new GroovyBlock(childNode, myAlignment, indent, myWrap, mySettings));
        prevChildNode = childNode;
      }
    }
    return subBlocks;
  }

}
