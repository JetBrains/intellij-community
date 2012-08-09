/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;


/**
 * @author Max Medvedev
 */
public class GrMethodCallWithSingleClosureArgFixer implements GrFixer {
  @Override
  public void apply(Editor editor, GroovySmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    final PsiElement parent = psiElement.getParent();
    if (parent instanceof GrReferenceExpression && !(parent.getParent() instanceof GrMethodCall) &&
        hasOnlyClosureParam((GrReferenceExpression)parent)) {

      final int endOffset = psiElement.getTextRange().getEndOffset();
      editor.getDocument().insertString(endOffset, "{\n}");
    }
  }

  private static boolean hasOnlyClosureParam(GrReferenceExpression ref) {
    if (ref.resolve() != null) return false;

    GroovyResolveResult[] results = ref.multiResolve(true);
    for (GroovyResolveResult result : results) {
      final PsiElement element = result.getElement();
      if (element instanceof PsiMethod) {
        final PsiParameter[] parameters = ((PsiMethod)element).getParameterList().getParameters();
        if (parameters.length == 1 && parameters[0].getType().equalsToText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
          return true;
        }
      }
    }
    return false;
  }
}
