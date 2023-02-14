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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;


class IndexingMethodConversionPredicate implements PsiElementPredicate {
  @Override
  public boolean satisfiedBy(@NotNull PsiElement element) {
    if (!(element instanceof GrMethodCallExpression callExpression)) {
      return false;
    }

    if (ErrorUtil.containsError(element)) {
      return false;
    }
    final GrArgumentList argList = callExpression.getArgumentList();
    final GrExpression[] arguments = argList.getExpressionArguments();

    final GrExpression invokedExpression = callExpression.getInvokedExpression();
    if (!(invokedExpression instanceof GrReferenceExpression referenceExpression)) {
      return false;
    }
    final GrExpression qualifier = referenceExpression.getQualifierExpression();
    if (qualifier == null) {
      return false;
    }
    final IElementType referenceType = referenceExpression.getDotTokenType();
    if (!GroovyTokenTypes.mDOT.equals(referenceType)) {
      return false;
    }
    final String methodName = referenceExpression.getReferenceName();
    if ("getAt".equals(methodName)) {
      return arguments.length == 1;
    }
    if ("get".equals(methodName)) {
      final PsiType qualifierType = qualifier.getType();
      if (!isMap(qualifierType)) {
        return false;
      }
      return arguments.length == 1;
    } else if ("setAt".equals(methodName)) {
      return arguments.length == 2;
    } else if ("put".equals(methodName)) {
      final PsiType qualifierType = qualifier.getType();
      if (!isMap(qualifierType)) {
        return false;
      }
      return arguments.length == 2;
    }
    return false;
  }

  private static boolean isMap(PsiType type) {
    return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP);
  }

}
