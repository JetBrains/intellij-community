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

package org.jetbrains.plugins.groovy.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class GroovyFoldingBuilder implements FoldingBuilder, GroovyElementTypes {

  public FoldingDescriptor[] buildFoldRegions(ASTNode node, Document document) {
    List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
    appendDescriptors(node.getPsi(), document, descriptors);
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  private void appendDescriptors(PsiElement element, Document document, List<FoldingDescriptor> descriptors) {
    ASTNode node = element.getNode();
    if (node == null) return;
    IElementType type = node.getElementType();

    if (GroovyElementTypes.BLOCK_SET.contains(type) || type == GroovyElementTypes.CLOSABLE_BLOCK) {
      if (isMultiline(element, document)) {
        descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
      }
    }

    // comments
    if ((type.equals(mML_COMMENT) || type.equals(GROOVY_DOC_COMMENT)) &&
        isMultiline(element, document) &&
        isWellEndedComment(element)) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
    }

    PsiElement child = element.getFirstChild();
    while (child != null) {
      appendDescriptors(child, document, descriptors);
      child = child.getNextSibling();
    }

  }

  private boolean isWellEndedComment(PsiElement element) {
    String text = element.getText();
    return text != null && text.endsWith("*/");
  }

  private boolean isMultiline(PsiElement element, Document document) {
    final TextRange range = element.getTextRange();
    return document.getLineNumber(range.getStartOffset()) < document.getLineNumber(range.getEndOffset());
  }

  public String getPlaceholderText(ASTNode node) {
    final IElementType elemType = node.getElementType();
    if (GroovyElementTypes.BLOCK_SET.contains(elemType) || elemType == GroovyElementTypes.CLOSABLE_BLOCK) {
      return "{...}";
    }
    if (elemType.equals(mML_COMMENT)) {
      return "/*...*/";
    }
    if (elemType.equals(GROOVY_DOC_COMMENT)) {
      return "/**...*/";
    }
    return null;
  }

  public boolean isCollapsedByDefault(ASTNode node) {
    return false;
  }
}
