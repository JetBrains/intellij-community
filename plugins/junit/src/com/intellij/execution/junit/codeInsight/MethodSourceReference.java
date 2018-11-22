// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.codeInsight;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    String methodName = getValue();
    String className = StringUtil.getPackageName(methodName, '#');
    boolean selfClassReference = className.isEmpty() ||
                                 ClassUtil.findPsiClass(getElement().getManager(), className, null, false, getElement().getResolveScope()) == null;
    return super.handleElementRename(selfClassReference ? newElementName : className + '#' + newElementName);
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    PsiClass cls = PsiTreeUtil.getParentOfType(getElement(), PsiClass.class);
    if (cls != null) {
      String methodName = getValue();
      String className = StringUtil.getPackageName(methodName, '#');
      if (!className.isEmpty()) {
        PsiClass aClass = ClassUtil.findPsiClass(cls.getManager(), className, null, false, cls.getResolveScope());
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

  @Override
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
    boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    return (TestUtils.testInstancePerClass(Objects.requireNonNull(method.getContainingClass())) != isStatic) && method.getParameterList().isEmpty();
  }
}
