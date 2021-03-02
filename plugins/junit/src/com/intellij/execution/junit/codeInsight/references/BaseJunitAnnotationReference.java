// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.codeInsight.references;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT;

public abstract class BaseJunitAnnotationReference extends PsiReferenceBase<PsiLanguageInjectionHost> {
  public BaseJunitAnnotationReference(PsiLanguageInjectionHost element) {
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
    String shortName = StringUtil.getShortName(methodName, '#');
    if (!shortName.equals(method.getName())) return false;
    PsiClass methodClass = method.getContainingClass();
    if (methodClass == null) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      String className = StringUtil.getPackageName(methodName, '#');
      if (!className.isEmpty()) {
        return className.equals(ClassUtil.getJVMClassName(methodClass));
      }
    }
    PsiClass psiClazz = null;
    UClass literalClazz = UastUtils.getParentOfType(myLiteral, UClass.class);
    if (literalClazz != null) {
      psiClazz = literalClazz.getPsi();
    }
    if (psiClazz == null) return false;
    if (InheritanceUtil.isInheritorOrSelf(psiClazz, methodClass, true) ||
        InheritanceUtil.isInheritorOrSelf(methodClass, psiClazz, true)) {
      return true;
    }
    return false;
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    UExpression myLiteral = UastContextKt.toUElement(getElement(), UExpression.class);
    if (myLiteral == null) return null;
    PsiClass psiClazz = null;
    UClass literalClazz = UastUtils.getParentOfType(myLiteral, UClass.class);
    if (literalClazz != null) {
      psiClazz = literalClazz.getPsi();
    }
    UMethod literalMethod = UastUtils.getParentOfType(myLiteral, UMethod.class);
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
          return filteredMethod(methods, literalClazz, literalMethod);
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
    return filteredMethod(clazzMethods, literalClazz, literalMethod);
  }

  @Nullable
  private PsiMethod filteredMethod(PsiMethod[] clazzMethods, UClass uClass, UMethod uMethod) {
    return Arrays.stream(clazzMethods)
      .filter(method -> hasNoStaticProblem(method, uClass, uMethod))
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
        if (current != null && !hasNoStaticProblem(method, topLevelClass, current)) continue;
        final LookupElementBuilder builder = LookupElementBuilder.create(method);
        list.add(builder.withAutoCompletionPolicy(AutoCompletionPolicy.SETTINGS_DEPENDENT));
      }
    }
    return list.toArray();
  }

  /**
   * @param method method referenced from within JUnit annotation
   * @param uClass the class where the annotation is located
   * @param uMethod the JUnit annotated method, is null in case the annotation is class-level
   * @return true in case static check is successful
   */
  abstract protected boolean hasNoStaticProblem(@NotNull PsiMethod method, @NotNull UClass uClass, @Nullable UMethod uMethod);
}
