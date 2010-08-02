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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public class IndexingMethodConversionIntention extends Intention {

  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new IndexingMethodConversionPredicate();
  }

  public void processIntention(@NotNull PsiElement element, Project project, Editor editor)
      throws IncorrectOperationException {
    final GrMethodCallExpression callExpression =
        (GrMethodCallExpression) element;
      final GrArgumentList argList = callExpression.getArgumentList();
      final GrExpression[] arguments = argList.getExpressionArguments();

      GrReferenceExpression methodExpression = (GrReferenceExpression) callExpression.getInvokedExpression();
      final IElementType referenceType = methodExpression.getDotTokenType();

      final String methodName = methodExpression.getName();
      final GrExpression qualifier = methodExpression.getQualifierExpression();
      if("getAt".equals(methodName)|| "get".equals(methodName))
      {
          IntentionUtils.replaceExpression(qualifier.getText() + '[' + arguments[0].getText() + ']',
                  callExpression);
      }
      else{
          IntentionUtils.replaceExpression(qualifier.getText() + '[' + arguments[0].getText() + "]=" +arguments[1].getText(),
                  callExpression);
      }
  }

}
