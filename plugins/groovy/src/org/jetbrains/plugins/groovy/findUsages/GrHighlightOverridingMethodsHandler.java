/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.highlighting.ChooseClassAndDoHighlightRunnable;
import com.intellij.codeInsight.highlighting.HighlightOverridingMethodsHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrHighlightOverridingMethodsHandler extends HighlightOverridingMethodsHandler {
  private final PsiElement myTarget;
  private final GrTypeDefinition myClass;

  public GrHighlightOverridingMethodsHandler(final Editor editor,
                                             final PsiFile file,
                                             final PsiElement target,
                                             final GrTypeDefinition psiClass) {
    super(editor, file, target, psiClass);
    myTarget = target;
    myClass = psiClass;
  }


  @Override
  public List<PsiClass> getTargets() {
    GrReferenceList list =
      GroovyTokenTypes.kEXTENDS == myTarget.getNode().getElementType() ? myClass.getExtendsClause() : myClass.getImplementsClause();
    if (list == null) return Collections.emptyList();
    final PsiClassType[] classTypes = list.getReferencedTypes();
    return ChooseClassAndDoHighlightRunnable.resolveClasses(classTypes);
  }
}
