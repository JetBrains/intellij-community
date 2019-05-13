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

package org.jetbrains.plugins.groovy.dsl.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author ilyas
 */
public class GrExpressionCategory implements PsiEnhancerCategory{

  public static Collection<GrExpression> getArguments(GrCallExpression call) {
    return Arrays.asList(call.getExpressionArguments());
  }

  @Nullable
  public static PsiClass getClassType(GrExpression expr) {
    final PsiType type = expr.getType();
    if (type instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType)type;
      return classType.resolve();
    } else {
      final String text = type.getPresentableText();
      final Project project = expr.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      return facade.findClass(text, GlobalSearchScope.allScope(project));
    }
  }

}
