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
package org.jetbrains.plugins.groovy.editor.selection;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author Maxim.Medvedev
 */
public class GroovyWordSelectionFilter implements Condition<PsiElement> {
  @Override
  public boolean value(PsiElement element) {
return !(element instanceof GroovyPsiElement || element.getLanguage() == GroovyLanguage.INSTANCE);
/*    final ASTNode node = element.getNode();
    if (node == null) return false;

    final IElementType type = node.getElementType();
    if (type == mIDENT ||
        type == mSTRING_LITERAL ||
        type == mGSTRING_LITERAL ||
        type == mGSTRING_CONTENT ||
        type == mREGEX_LITERAL ||
        type == mDOLLAR_SLASH_REGEX_LITERAL ||
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

      ;*/
  }
}
