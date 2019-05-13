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
package org.jetbrains.plugins.groovy.lang.psi.impl.search;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Medvedev
 */
public class GrPrivateFieldScopeEnlarger extends UseScopeEnlarger {
  @Override
  public SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
    if (element instanceof PsiField && ((PsiField)element).hasModifierProperty(PsiModifier.PRIVATE)) {
      PsiClass containingClass = ((PsiField)element).getContainingClass();
      if (containingClass != null && PsiUtil.isLocalOrAnonymousClass(containingClass)) return null;
      final GlobalSearchScope maximalUseScope = ResolveScopeManager.getElementUseScope(element);
      return new GrSourceFilterScope(maximalUseScope);
    }

    return null;
  }
}
