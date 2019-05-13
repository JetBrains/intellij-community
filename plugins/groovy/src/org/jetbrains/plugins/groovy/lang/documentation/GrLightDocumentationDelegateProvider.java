/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.documentation;

import com.intellij.codeInsight.javadoc.DocumentationDelegateProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

public class GrLightDocumentationDelegateProvider extends DocumentationDelegateProvider {

  @Nullable
  @Override
  public PsiDocCommentOwner computeDocumentationDelegate(@NotNull PsiMember member) {
    if (!(member instanceof PsiMethod) || member.isPhysical()) return null;

    String data = member.getUserData(ResolveUtil.DOCUMENTATION_DELEGATE_FQN);
    if (StringUtil.isEmptyOrSpaces(data)) return null;

    PsiClass clazz = JavaPsiFacade.getInstance(member.getProject()).findClass(data, member.getResolveScope());
    return clazz == null ? null : clazz.findMethodBySignature((PsiMethod)member, false);
  }
}
