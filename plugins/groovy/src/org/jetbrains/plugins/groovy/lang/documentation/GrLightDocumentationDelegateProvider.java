// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.documentation;

import com.intellij.codeInsight.javadoc.DocumentationDelegateProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

public final class GrLightDocumentationDelegateProvider extends DocumentationDelegateProvider {

  @Override
  public @Nullable PsiDocCommentOwner computeDocumentationDelegate(@NotNull PsiMember member) {
    if (!(member instanceof PsiMethod) || member.isPhysical()) return null;

    String data = member.getUserData(ResolveUtil.DOCUMENTATION_DELEGATE_FQN);
    if (StringUtil.isEmptyOrSpaces(data)) return null;

    PsiClass clazz = JavaPsiFacade.getInstance(member.getProject()).findClass(data, member.getResolveScope());
    return clazz == null ? null : clazz.findMethodBySignature((PsiMethod)member, false);
  }
}
