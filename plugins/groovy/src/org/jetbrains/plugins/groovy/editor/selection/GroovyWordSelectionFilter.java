/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.editor.selection;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 * @author Maxim.Medvedev
 */
public class GroovyWordSelectionFilter implements Condition<PsiElement> {
  public boolean value(PsiElement element) {

    final ASTNode node = element.getNode();
    if (node == null) return false;

    final IElementType type = node.getElementType();
    if (type == mIDENT ||
        type == mSTRING_LITERAL ||
        type == mGSTRING_LITERAL ||
        type == mGSTRING_CONTENT ||
        type == mGDOC_COMMENT_DATA ||
        type == mGDOC_TAG_NAME ||
        type == mGDOC_TAG_PLAIN_VALUE_TOKEN ||
        type == mGDOC_TAG_VALUE_TOKEN ||
        type == mREGEX_BEGIN ||
        type == mREGEX_CONTENT ||
        type == mREGEX_END ||
        type == mDOLLAR_SLASH_REGEX_BEGIN ||
        type == mDOLLAR_SLASH_REGEX_CONTENT ||
        type == mDOLLAR_SLASH_REGEX_END) {
      return true;
    }

    return !(element instanceof GrCodeBlock) &&
           !(element instanceof GrListOrMap) &&
           !(element instanceof GrParameterList) &&
           !(element instanceof GrArgumentList) &&
           !(type == mDOLLAR)

      ;
  }
}
