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
package org.jetbrains.plugins.groovy.refactoring.introduce;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.List;

/**
 * Created by Max Medvedev on 8/29/13
 */
public abstract class GrIntroduceFieldHandlerBase<Settings extends GrIntroduceSettings> extends GrIntroduceHandlerBase<Settings, PsiClass> {
  @NotNull
  @Override
  protected PsiClass[] findPossibleScopes(GrExpression expression,
                                          GrVariable variable,
                                          StringPartInfo partInfo,
                                          Editor editor) {
    PsiElement place = getCurrentPlace(expression, variable, partInfo);
    PsiClass aClass = PsiUtil.getContextClass(place);
    if (aClass instanceof GroovyScriptClass) {
      return new PsiClass[]{aClass};
    }
    else {
      List<PsiClass> result = ContainerUtil.newArrayList(aClass);
      while (aClass != null) {
        aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
        ContainerUtil.addIfNotNull(result, aClass);
      }
      return result.toArray(new PsiClass[result.size()]);
    }
  }

  @Override
  protected void showScopeChooser(PsiClass[] scopes, final Pass<PsiClass> callback, Editor editor) {
    PsiElementProcessor<PsiClass> processor = new PsiElementProcessor<PsiClass>() {
      @Override
      public boolean execute(@NotNull PsiClass element) {
        callback.pass(element);
        return false;
      }
    };

    NavigationUtil.getPsiElementPopup(scopes, new PsiClassListCellRenderer(), "Choose class to introduce field", processor).showInBestPositionFor(editor);
  }

  @NotNull
  @Override
  protected PsiElement[] findOccurrences(@NotNull GrExpression expression, @NotNull PsiElement scope) {
    if (scope instanceof GroovyScriptClass) {
      scope = scope.getContainingFile();
    }
    return super.findOccurrences(expression, scope);
  }
}
