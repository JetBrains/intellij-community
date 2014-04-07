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
package org.jetbrains.plugins.gradle.service.resolve;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import java.util.List;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 7/23/13 4:21 PM
 */
public class GradleScriptContributor extends NonCodeMembersContributor {

  public static final Set<String> BUILD_PROJECT_SCRIPT_BLOCKS = ContainerUtil.newHashSet(
    "project",
    "configure",
    "subprojects",
    "allprojects",
    "buildscript"
  );


  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     @Nullable PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    if (!(aClass instanceof GroovyScriptClass)) {
      return;
    }

    PsiFile file = aClass.getContainingFile();
    if (file == null || !FileUtilRt.extensionEquals(file.getName(), GradleConstants.EXTENSION)
        || GradleConstants.SETTINGS_FILE_NAME.equals(file.getName())) return;

    List<String> methodInfo = ContainerUtilRt.newArrayList();
    for (GrMethodCall current = PsiTreeUtil.getParentOfType(place, GrMethodCall.class);
         current != null;
         current = PsiTreeUtil.getParentOfType(current, GrMethodCall.class)) {
      GrExpression expression = current.getInvokedExpression();
      if (expression == null) {
        continue;
      }
      String text = expression.getText();
      if (text != null) {
        methodInfo.add(text);
      }
    }

    final String methodCall = ContainerUtil.getLastItem(methodInfo);
    if (methodInfo.size() > 1 && BUILD_PROJECT_SCRIPT_BLOCKS.contains(methodCall)) {
      methodInfo.remove(methodInfo.size() - 1);
    }

    for (GradleMethodContextContributor contributor : GradleMethodContextContributor.EP_NAME.getExtensions()) {
      contributor.process(methodInfo, processor, state, place);
    }
  }
}
