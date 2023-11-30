// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.changeSignature.*;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTagValueToken;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.List;

public class GrChangeSignatureUsageProvider implements ChangeSignatureUsageProvider {
  private static final Logger LOG = Logger.getInstance(GrChangeSignatureUsageProvider.class);

  @Override
  public @Nullable UsageInfo createOverrideUsageInfo(@NotNull ChangeInfo changeInfo,
                                                     @NotNull PsiElement overrider,
                                                     @NotNull PsiElement method,
                                                     boolean isOriginalOverrider,
                                                     boolean modifyArgs,
                                                     boolean modifyExceptions,
                                                     List<? super UsageInfo> result) {
    LOG.assertTrue(overrider instanceof PsiMethod);
    JavaChangeInfo javaChangeInfo = JavaChangeInfoConverters.getJavaChangeInfo(changeInfo, new UsageInfo(overrider));
    if (javaChangeInfo == null) return null;

    return new OverriderUsageInfo((PsiMethod)overrider, javaChangeInfo.getMethod(), isOriginalOverrider, modifyArgs, modifyExceptions);
  }

  @Override
  public @Nullable UsageInfo createUsageInfo(@NotNull ChangeInfo changeInfo,
                                             @NotNull PsiReference reference,
                                             @NotNull PsiElement m,
                                             boolean isToModifyArgs,
                                             boolean isToThrowExceptions) {
    JavaChangeInfo javaChangeInfo = JavaChangeInfoConverters.getJavaChangeInfo(changeInfo, new UsageInfo(reference));
    if (javaChangeInfo == null) return null;
    PsiMethod method = javaChangeInfo.getMethod();
    if (m instanceof PsiMethod) {
      //in case of propagation, completely different method
      //todo if it's foreign language override, keep base method and hope it's fine
      method = (PsiMethod)m;
    }

    PsiElement element = reference.getElement();
    boolean isToCatchExceptions = isToThrowExceptions &&
                                  needToCatchExceptions(RefactoringUtil.getEnclosingMethod(element), javaChangeInfo);
    if (PsiUtil.isMethodUsage(element)) {
      return new GrMethodCallUsageInfo(element, isToModifyArgs, isToCatchExceptions, method);
    }
    else if (element instanceof GrDocTagValueToken) {
      return new UsageInfo(reference.getElement());
    }
    else if (element instanceof GrMethod && ((GrMethod)element).isConstructor()) {
      return new DefaultConstructorImplicitUsageInfo((GrMethod)element, ((GrMethod)element).getContainingClass(), method);
    }
    else if (element instanceof PsiClass psiClass) {
      LOG.assertTrue(method.isConstructor());
      if (psiClass instanceof GrAnonymousClassDefinition) {
        return new GrMethodCallUsageInfo(element, isToModifyArgs, isToCatchExceptions, method);
      }
      /*if (!(myChangeInfo instanceof JavaChangeInfoImpl)) continue; todo propagate methods
      if (shouldPropagateToNonPhysicalMethod(method, result, psiClass,
                                             ((JavaChangeInfoImpl)myChangeInfo).propagateParametersMethods)) {
        continue;
      }
      if (shouldPropagateToNonPhysicalMethod(method, result, psiClass,
                                             ((JavaChangeInfoImpl)myChangeInfo).propagateExceptionsMethods)) {
        continue;
      }*/
      return new NoConstructorClassUsageInfo(psiClass);
    }
    else if (reference instanceof PsiCallReference) {
      return new CallReferenceUsageInfo((PsiCallReference)reference);
    }
    else {
      return new MoveRenameUsageInfo(element, reference, method);
    }
  }

  private static boolean needToCatchExceptions(PsiMethod caller, JavaChangeInfo info) {
    /*if (myChangeInfo instanceof JavaChangeInfoImpl) { //todo propagate methods
      return myChangeInfo.isExceptionSetOrOrderChanged() &&
             !((JavaChangeInfoImpl)myChangeInfo).propagateExceptionsMethods.contains(caller);
    }
    else {*/
    return info.isExceptionSetOrOrderChanged();
  }
}
