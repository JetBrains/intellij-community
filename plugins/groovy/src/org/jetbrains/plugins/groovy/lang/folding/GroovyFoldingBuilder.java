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
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class GroovyFoldingBuilder implements FoldingBuilder, GroovyElementTypes {

  public FoldingDescriptor[] buildFoldRegions(ASTNode node, Document document) {
    List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
    appendDescriptors(node, document, descriptors);
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  private void appendDescriptors(ASTNode node, Document document, List<FoldingDescriptor> descriptors) {
    // Method body
    final PsiElement element = node.getPsi();
    assert element != null;

    if (element instanceof GrMethod) {
      GrMethod method = (GrMethod) element;
      GrOpenBlock body = method.getBlock();
      if (body != null) {
        ASTNode bodyNode = body.getNode();
        if (bodyNode != null && isMultiline(bodyNode, document)) {
          descriptors.add(new FoldingDescriptor(bodyNode, bodyNode.getTextRange()));
        }
      }
    } else if (element instanceof GrClosableBlock && isMultiline(node, document)) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
    }

    // class body
    if (element.getParent() != null &&
            element instanceof GrTypeDefinition &&
            !(element.getParent() instanceof GroovyFile)) {
      GrTypeDefinition typeDef = (GrTypeDefinition) element;
      GrTypeDefinitionBody body = typeDef.getBody();
      if (body != null) {
        ASTNode bodyNode = body.getNode();
        if (bodyNode != null && isMultiline(bodyNode, document)) {
          descriptors.add(new FoldingDescriptor(bodyNode, bodyNode.getTextRange()));
        }
      }
    }

    // comments
    if (node.getElementType().equals(mML_COMMENT) && isMultiline(node, document)) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
    }

    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      appendDescriptors(child, document, descriptors);
      child = child.getTreeNext();
    }

  }

  private boolean isMultiline(ASTNode node, Document document) {
    final TextRange range = node.getTextRange();
    return document.getLineNumber(range.getStartOffset()) < document.getLineNumber(range.getEndOffset());
  }

  public String getPlaceholderText(ASTNode node) {
    final IElementType elemType = node.getElementType();
    if (GroovyElementTypes.BLOCK_SET.contains(elemType) || elemType == GroovyElementTypes.CLOSABLE_BLOCK) {
      return "{...}";
    }
    if (elemType.equals(mML_COMMENT)) {
      return node.getText().startsWith("/**") ? "/**...*/" : "/*...*/";
    }
    return null;
  }

  public boolean isCollapsedByDefault(ASTNode node) {
    return false;
  }
}
