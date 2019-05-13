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
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author Max Medvedev
 */
public class GrHighlightHandlerFactory extends HighlightUsagesHandlerFactoryBase {
  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target) {
    ASTNode node = target.getNode();
    if (node == null) return null;

    IElementType type = node.getElementType();
    if (type == GroovyTokenTypes.kIMPLEMENTS || type == GroovyTokenTypes.kEXTENDS) {
      PsiElement parent = target.getParent();
      if (!(parent instanceof GrReferenceList)) return null;
      PsiElement grand = parent.getParent();
      if (!(grand instanceof GrTypeDefinition)) return null;
      return new GrHighlightOverridingMethodsHandler(editor, file, target, (GrTypeDefinition)grand);
    }
    else if (type == GroovyTokenTypes.kRETURN || type == GroovyTokenTypes.kTHROW) {
      return new GrHighlightExitPointHandler(editor, file, target);
    }
    return null;
  }
}
