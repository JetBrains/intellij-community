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
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;

public class MethodSourceReference extends PsiReferenceBase<PsiLanguageInjectionHost> {

  public MethodSourceReference(PsiLanguageInjectionHost element) {
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
                                 ClassUtil
                                   .findPsiClass(getElement().getManager(), className, null, false, getElement().getResolveScope()) == null;
    return super.handleElementRename(selfClassReference ? newElementName : className + '#' + newElementName);
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    UExpression myLiteral = UastContextKt.toUElement(getElement(), UExpression.class);
    if (myLiteral == null) return false;
    UMethod uMethod = UastContextKt.toUElement(element, UMethod.class);
    if (uMethod == null) return false;
    PsiMethod method = uMethod.getJavaPsi();
    String methodName = (String)myLiteral.evaluate();
    if (methodName == null) return false;
    methodName = StringUtil.getShortName(methodName, '#');
    if (!methodName.equals(method.getName())) return false;
    PsiClass psiClazz = getPsiClazz(myLiteral);
    if (psiClazz == null) return false;
    PsiClass methodClass = PsiTreeUtil.getParentOfType(method, PsiClass.class);
    if (methodClass == null) return false;
    if (psiClazz.isInheritor(methodClass, false) || methodClass.isInheritor(psiClazz, false)) {
      return true;
    }
    return false;
  }

  @Nullable
  private static PsiClass getPsiClazz(UExpression myLiteral) {
    UClass clazz = UastUtils.getParentOfType(myLiteral, UClass.class);
    if (clazz == null) return null;
    return clazz.getPsi();
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    UExpression myLiteral = UastContextKt.toUElement(getElement(), UExpression.class);
    if (myLiteral == null) return null;
    PsiClass psiClazz = getPsiClazz(myLiteral);
    if (psiClazz == null) return null;
    String methodName = (String)myLiteral.evaluate();
    if (methodName == null) return null;
    String className = StringUtil.getPackageName(methodName, '#');
    if (!className.isEmpty()) {
      PsiClass aClass = ClassUtil.findPsiClass(psiClazz.getManager(), className, null, false, psiClazz.getResolveScope());
      if (aClass != null) {
        psiClazz = aClass;
        methodName = StringUtil.getShortName(methodName, '#');
      }
    }
    PsiMethod[] clazzMethods = psiClazz.findMethodsByName(methodName, true);
    if (clazzMethods.length == 0 && (psiClazz.isInterface() || PsiUtil.isAbstractClass(psiClazz))) {
      final String finalMethodName = methodName;
      PsiElementResolveResult neededMethod = ClassInheritorsSearch.search(psiClazz, psiClazz.getResolveScope(), false)
        .mapping(aClazz -> {
          final PsiMethod[] methods = aClazz.findMethodsByName(finalMethodName, false);
          return filteredMethod(methods, aClazz);
        })
        .filtering(method -> {
          return method != null;
        })
        .mapping(method -> {
          return new PsiElementResolveResult(method);
        })
        .findFirst();
      if (neededMethod == null) return null;
      return neededMethod.getElement();
    }
    return filteredMethod(clazzMethods, psiClazz);
  }

  @Nullable
  private static PsiMethod filteredMethod(PsiMethod[] clazzMethods, PsiClass finalCls) {
    return Arrays.stream(clazzMethods)
      .filter(method -> staticOrOneInstancePerClassNoParams(method, finalCls))
      .findFirst()
      .orElse(clazzMethods.length == 0 ? null : clazzMethods[0]);
  }

  @Override
  public Object @NotNull [] getVariants() {
    UExpression myLiteral = UastContextKt.toUElement(getElement(), UExpression.class);
    final List<Object> list = new ArrayList<>();
    if (myLiteral == null) return list.toArray();
    final UClass topLevelClass = UastUtils.getParentOfType(myLiteral, UClass.class);
    if (topLevelClass != null) {
      final UMethod current = UastUtils.getParentOfType(myLiteral, UMethod.class);
      PsiClass psiTopLevelClass = topLevelClass.getJavaPsi();
      final PsiMethod[] methods = psiTopLevelClass.getAllMethods();
      for (PsiMethod method : methods) {
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) continue;
        if (JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) continue;
        if (current != null && method.getName().equals(current.getName())) continue;
        if (!staticOrOneInstancePerClassNoParams(method, psiTopLevelClass)) continue;
        final LookupElementBuilder builder = LookupElementBuilder.create(method);
        list.add(builder.withAutoCompletionPolicy(AutoCompletionPolicy.SETTINGS_DEPENDENT));
      }
    }
    return list.toArray();
  }

  private static boolean staticOrOneInstancePerClassNoParams(PsiMethod method, PsiClass psiClass) {
    boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    return (TestUtils.testInstancePerClass(psiClass) != isStatic) && method.getParameterList().isEmpty();
  }
}
