// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.introduce;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.List;
import java.util.function.Consumer;

public abstract class GrIntroduceFieldHandlerBase<Settings extends GrIntroduceSettings> extends GrIntroduceHandlerBase<Settings, PsiClass> {
  @Override
  public PsiClass @NotNull [] findPossibleScopes(GrExpression expression,
                                                 GrVariable variable,
                                                 StringPartInfo partInfo,
                                                 Editor editor) {
    PsiElement place = getCurrentPlace(expression, variable, partInfo);
    PsiClass aClass = PsiUtil.getContextClass(place);
    if (aClass instanceof GroovyScriptClass) {
      return new PsiClass[]{aClass};
    }
    else {
      List<PsiClass> result = new SmartList<>(aClass);
      while (aClass != null) {
        aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
        ContainerUtil.addIfNotNull(result, aClass);
      }
      return result.toArray(PsiClass.EMPTY_ARRAY);
    }
  }

  @Override
  protected void showScopeChooser(PsiClass[] scopes, final Consumer<? super PsiClass> callback, Editor editor) {
    PsiElementProcessor<PsiClass> processor = new PsiElementProcessor<>() {
      @Override
      public boolean execute(@NotNull PsiClass element) {
        callback.accept(element);
        return false;
      }
    };

    NavigationUtil.getPsiElementPopup(scopes, new PsiClassListCellRenderer(),
                                      JavaRefactoringBundle.message("popup.title.choose.class.to.introduce.field"), processor).showInBestPositionFor(editor);
  }

  @Override
  protected PsiElement @NotNull [] findOccurrences(@NotNull GrExpression expression, @NotNull PsiElement scope) {
    if (scope instanceof GroovyScriptClass) {
      scope = scope.getContainingFile();
    }
    return super.findOccurrences(expression, scope);
  }
}
