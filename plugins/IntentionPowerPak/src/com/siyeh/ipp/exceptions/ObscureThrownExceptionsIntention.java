// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.exceptions;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public class ObscureThrownExceptionsIntention extends MutablyNamedIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("obscure.thrown.exceptions.intention.family.name");
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ObscureThrownExceptionsPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    if (!(element instanceof PsiReferenceList referenceList)) {
      return;
    }
    final PsiClassType[] types = referenceList.getReferencedTypes();
    final PsiClass commonSuperClass = findCommonSuperClass(types);
    if (commonSuperClass == null) {
      return;
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    final PsiJavaCodeReferenceElement referenceElement = factory.createClassReferenceElement(commonSuperClass);
    final PsiReferenceList newReferenceList = factory.createReferenceList(new PsiJavaCodeReferenceElement[]{referenceElement});
    new CommentTracker().replaceAndRestoreComments(referenceList, newReferenceList);
  }

  @Nullable
  public static PsiClass findCommonSuperClass(PsiClassType... types) {
    if (types.length == 0) {
      return null;
    }
    final PsiClass firstClass = types[0].resolve();
    if (firstClass == null || types.length == 1) {
      return firstClass;
    }
    Set<PsiClass> sourceSet = new HashSet<>();
    PsiClass aClass = firstClass;
    while (aClass != null) {
      sourceSet.add(aClass);
      aClass = aClass.getSuperClass();
    }
    if (sourceSet.isEmpty()) {
      return null;
    }
    Set<PsiClass> targetSet = new HashSet<>();
    final int max = types.length - 1;
    for (int i = 1; i < max; i++) {
      final PsiClassType classType = types[i];
      PsiClass aClass1 = classType.resolve();
      while (aClass1 != null) {
        if (sourceSet.contains(aClass1)) {
          targetSet.add(aClass1);
        }
        aClass1 = aClass1.getSuperClass();
      }
      sourceSet = targetSet;
      targetSet = new HashSet<>();
    }
    PsiClass aClass1 = types[max].resolve();
    while (aClass1 != null && !sourceSet.contains(aClass1)) {
      aClass1 = aClass1.getSuperClass();
    }
    return aClass1;
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiReferenceList referenceList = (PsiReferenceList)element;
    final PsiClassType[] types = referenceList.getReferencedTypes();
    final PsiClass commonSuperClass = findCommonSuperClass(types);
    if (commonSuperClass == null || !InheritanceUtil.isInheritor(commonSuperClass, CommonClassNames.JAVA_LANG_THROWABLE)) {
      return null;
    }
    return CommonQuickFixBundle.message("fix.replace.with.x", "throws " + commonSuperClass.getName());
  }
}
