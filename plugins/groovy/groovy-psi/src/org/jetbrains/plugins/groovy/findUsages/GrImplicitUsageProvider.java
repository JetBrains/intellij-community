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

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public class GrImplicitUsageProvider implements ImplicitUsageProvider {
  @Override
  public boolean isImplicitUsage(PsiElement element) {
    if (element instanceof GrMethod) {
      final GrMethod method = (GrMethod)element;

      if (PsiUtil.OPERATOR_METHOD_NAMES.contains(method.getName())) return true;

      if (MissingMethodAndPropertyUtil.isPropertyMissing(method)) return true;
      if (MissingMethodAndPropertyUtil.isMethodMissing(method)) return true;
    }
    else if (element instanceof GrParameter) {
      final GrParameter parameter = (GrParameter)element;

      final PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof GrMethod && (MissingMethodAndPropertyUtil.isMethodMissing((GrMethod)scope) || MissingMethodAndPropertyUtil
        .isPropertyMissing((GrMethod)scope))) return true;
    }

    return false;
  }

  @Override
  public boolean isImplicitRead(PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(PsiElement element) {
    return false;
  }
}
