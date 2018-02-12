/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;

import java.util.List;

public class GroovyAnonymousUnwrapper extends GroovyUnwrapper {
  public GroovyAnonymousUnwrapper() {
    super(CodeInsightBundle.message("unwrap.anonymous"));
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return e instanceof GrAnonymousClassDefinition
           && ((GrAnonymousClassDefinition)e).getMethods().length <= 1;
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return findElementToExtractFrom(e);
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiElement from = findElementToExtractFrom(element);

    for (PsiMethod m : ((PsiAnonymousClass)element).getMethods()) {
      //context.extractFromCodeBlock(m.getBody(), from);
    }

    PsiElement next = from.getNextSibling();
    if (PsiUtil.isJavaToken(next, JavaTokenType.SEMICOLON)) {
      context.deleteExactly(from.getNextSibling());
    }
    context.deleteExactly(from);
  }

  private static PsiElement findElementToExtractFrom(PsiElement el) {
    if (el.getParent() instanceof PsiNewExpression) el = el.getParent();
    el = findTopmostParentOfType(el, PsiMethodCallExpression.class);
    el = findTopmostParentOfType(el, PsiAssignmentExpression.class);
    el = findTopmostParentOfType(el, PsiDeclarationStatement.class);

    while (el.getParent() instanceof PsiExpressionStatement) {
      el = el.getParent();
    }

    return el;
  }

  private static PsiElement findTopmostParentOfType(PsiElement el, Class<? extends PsiElement> clazz) {
    while (true) {
      @SuppressWarnings({"unchecked"})
      PsiElement temp = PsiTreeUtil.getParentOfType(el, clazz, true, PsiAnonymousClass.class);
      if (temp == null || temp instanceof PsiFile) return el;
      el = temp;
    }
  }
}