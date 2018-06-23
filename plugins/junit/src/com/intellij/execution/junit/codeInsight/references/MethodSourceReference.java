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
package com.intellij.execution.junit.codeInsight.references;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MethodSourceReference extends PsiReferenceBase<PsiLiteral> {

  public MethodSourceReference(PsiLiteral element) {
    super(element, false);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiMethod) {
      return handleElementRename(((PsiMethod)element).getName());
    }
    return super.bindToElement(element);
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    String methodName = getValue();
    String className = StringUtil.getPackageName(methodName, '#');
    boolean selfClassReference = className.isEmpty() ||
                JavaPsiFacade.getInstance(getElement().getProject()).findClass(className, getElement().getResolveScope()) == null;
    return super.handleElementRename(selfClassReference ? newElementName : className + '#' + newElementName);
  }

  @Nullable
  public PsiElement resolve() {
    PsiClass cls = PsiTreeUtil.getParentOfType(getElement(), PsiClass.class);
    if (cls != null) {
      String methodName = getValue();
      String className = StringUtil.getPackageName(methodName, '#');
      if (!className.isEmpty()) {
        PsiClass aClass = JavaPsiFacade.getInstance(cls.getProject()).findClass(className, cls.getResolveScope());
        if (aClass != null) {
          cls = aClass;
          methodName = StringUtil.getShortName(methodName, '#');
        }
      }
      PsiMethod[] methods = cls.findMethodsByName(methodName, false);
      return Arrays.stream(methods)
        .filter(MethodSourceReference::staticNoParams)
        .findFirst()
        .orElse(methods.length == 0 ? null : methods[0]);
    }
    return null;
  }

  @NotNull
  public Object[] getVariants() {
    final List<Object> list = new ArrayList<>();
    final PsiClass topLevelClass = PsiTreeUtil.getParentOfType(getElement(), PsiClass.class);
    if (topLevelClass != null) {
      final PsiMethod current = PsiTreeUtil.getParentOfType(getElement(), PsiMethod.class);
      final PsiMethod[] methods = topLevelClass.getMethods();
      for (PsiMethod method : methods) {
        if (current != null && method.getName().equals(current.getName())) continue;
        if (!staticNoParams(method)) continue;
        final LookupElementBuilder builder = LookupElementBuilder.create(method);
        list.add(builder.withAutoCompletionPolicy(AutoCompletionPolicy.SETTINGS_DEPENDENT));
      }
    }
    return list.toArray();
  }

  private static boolean staticNoParams(PsiMethod method) {
    return method.hasModifierProperty(PsiModifier.STATIC) && method.getParameterList().isEmpty();
  }
}
