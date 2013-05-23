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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.*;

/**
 * User: anna
 */
public class DevKitEntryPoints implements ImplicitUsageProvider {

  @Override
  public boolean isImplicitUsage(PsiElement element) {
    if (element instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)element;
      if (psiClass.isEnum() ||
          psiClass.isAnnotationType() ||
          psiClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        return false;
      }

      final PsiClass domClass =
        JavaPsiFacade.getInstance(element.getProject()).findClass("com.intellij.util.xml.DomElement", element.getResolveScope());
      if (domClass != null && psiClass.isInheritor(domClass, true)) {
        return true;
      }
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
