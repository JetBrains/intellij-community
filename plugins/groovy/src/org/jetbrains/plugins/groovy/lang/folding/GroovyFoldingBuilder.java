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

import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;

import java.util.List;
import java.util.ArrayList;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;

/**
 * @author Ilya.Sergey
 */
public class GroovyFoldingBuilder implements FoldingBuilder, GroovyElementTypes {

  public FoldingDescriptor[] buildFoldRegions(ASTNode node, Document document) {
    List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
    appendDescriptors(node, document, descriptors);
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  private void appendDescriptors(ASTNode node, Document document, List<FoldingDescriptor> descriptors) {

    node.getPsi().getChildren();

    // Method body
    if (node.getPsi() != null && node.getPsi() instanceof GrMethod) {
      GrMethod method = (GrMethod) node.getPsi();
      GrOpenBlock body = method.getBody();
      if (body != null) {
        ASTNode myNode = body.getNode();
        if (myNode != null) {
          descriptors.add(new FoldingDescriptor(myNode, myNode.getTextRange()));
        }
      }
    }

    // Inner class or interface body
    if (node.getPsi() != null && node.getPsi().getParent() != null &&
            node.getPsi() instanceof GrTypeDefinition &&
            !(node.getPsi().getParent() instanceof GroovyFile)) {
      GrTypeDefinition typeDef = (GrTypeDefinition) node.getPsi();
      GrBody body = typeDef.getBody();
      if (body != null) {
        ASTNode myNode = body.getNode();
        if (myNode != null &&
                (myNode.getText().contains("\n") || myNode.getText().contains("\t"))) {
          descriptors.add(new FoldingDescriptor(myNode, myNode.getTextRange()));
        }
      }
    }

    // Doc comments
    if (node.getElementType().equals(mML_COMMENT) &&
            node.getText().substring(0, 3).equals("/**") &&
            (node.getText().contains("\n") || node.getText().contains("\r"))) {
      descriptors.add(new FoldingDescriptor(node, node.getTextRange()));
    }

    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      appendDescriptors(child, document, descriptors);
      child = child.getTreeNext();
    }

  }

  public String getPlaceholderText(ASTNode node) {
    if (node.getPsi() instanceof GrCodeBlock) {
      return "{...}";
    }
    if (node.getElementType().equals(mML_COMMENT) &&
            node.getText().substring(0, 3).equals("/**") &&
            (node.getText().contains("\n") || node.getText().contains("\r"))) {
      return "/**...*/";
    }
    return null;
  }

  public boolean isCollapsedByDefault(ASTNode node) {
    return false;
  }
}
