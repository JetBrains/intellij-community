/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Max Medvedev
 */
public class GrDGMTypeCalculator extends GrCallExpressionTypeCalculator {
  @Override
  protected PsiType calculateReturnType(@NotNull GrMethodCall callExpression, @NotNull PsiMethod resolvedMethod) {
    if (resolvedMethod instanceof GrGdkMethod) {
      resolvedMethod = ((GrGdkMethod)resolvedMethod).getStaticMethod();
    }

    final PsiClass containingClass = resolvedMethod.getContainingClass();
    if (containingClass == null || !GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals(containingClass.getQualifiedName())) return null;

    final String name = resolvedMethod.getName();

    if ("find".equals(name)) {
      final GrExpression invoked = callExpression.getInvokedExpression();
      if (invoked instanceof GrReferenceExpression) {
        final GrExpression qualifier = ((GrReferenceExpression)invoked).getQualifier();
        if (qualifier != null) {
          final PsiType type = qualifier.getType();
          if (type instanceof PsiArrayType) return ((PsiArrayType)type).getComponentType();
        }
      }
    }

    return null;
  }
}
