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
package org.jetbrains.plugins.gradle.service.resolve;

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 7/23/13 4:21 PM
 */
public class GradleScriptContributor extends NonCodeMembersContributor {

  // TODO den add doc
  @NotNull private static final Map<String, String> CONTRIBUTORS = ContainerUtilRt.newHashMap();
  static {
    CONTRIBUTORS.put("repositories", "org.gradle.api.artifacts.dsl.RepositoryHandler");
    CONTRIBUTORS.put("dependencies", "org.gradle.api.artifacts.dsl.DependencyHandler");
  }

  @NotNull @NonNls private static final String ROOT_CONTRIBUTOR_CLASS_NAME = "org.gradle.api.Project";

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     PsiScopeProcessor processor,
                                     PsiElement place,
                                     ResolveState state) {
    if (place == null) {
      return;
    }

    if (!(aClass instanceof GroovyScriptClass)) {
      return;
    }

    PsiFile file = aClass.getContainingFile();
    if (file == null || !file.getName().endsWith(GradleConstants.EXTENSION)) {
      return;
    }

    GroovyPsiManager psiManager = GroovyPsiManager.getInstance(aClass.getProject());
    String contributorClassName = ROOT_CONTRIBUTOR_CLASS_NAME;
    for (GrMethodCall current = PsiTreeUtil.getParentOfType(place, GrMethodCall.class);
         current != null;
         current = PsiTreeUtil.getParentOfType(current, GrMethodCall.class))
    {
      GrExpression expression = current.getInvokedExpression();
      if (expression != null) {
        String text = expression.getText();
        if (text == null || text.equals(place.getText())) {
          continue;
        }
        String s = CONTRIBUTORS.get(text);
        if (s != null) {
          contributorClassName = s;
          break;
        }
      }
    }

    PsiClass contributorClass = psiManager.findClassWithCache(contributorClassName, place.getResolveScope());
    if (contributorClass != null) {
      contributorClass.processDeclarations(processor, state, null, place);
    }
  }
}
