// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import java.util.List;

public class PropertyRenameHandler implements RenameHandler, TitledHandler {
  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    final PsiElement element = getElement(dataContext);
    if (element instanceof GrField && ((GrField)element).isProperty()) return true;
    if (element instanceof GrAccessorMethod) return true;
    if (element instanceof GrMethod && GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)element)) return true;
    return false;
  }

  @Nullable
  private static PsiElement getElement(DataContext dataContext) {
    return CommonDataKeys.PSI_ELEMENT.getData(dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, @Nullable DataContext dataContext) {
    final PsiElement element = getElement(dataContext);
    invokeInner(project, editor, element);
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, @Nullable DataContext dataContext) {
    PsiElement element = elements.length == 1 ? elements[0] : null;
    if (element == null) element = getElement(dataContext);
    Editor editor = dataContext == null ? null : CommonDataKeys.EDITOR.getData(dataContext);
    invokeInner(project, editor, element);
  }

  private static void invokeInner(Project project, Editor editor, PsiElement element) {
    final Pair<List<? extends PsiElement>, String> pair = RenamePropertyUtil.askToRenameProperty((PsiMember)element);
    final List<? extends PsiElement> result = pair.getFirst();
    if (result.isEmpty()) return;

    PsiElement propertyToRename = getPropertyToRename(element, result, pair.getSecond());

    PsiElementRenameHandler.invoke(propertyToRename, project, element, editor);
  }

  private static PsiElement getPropertyToRename(PsiElement element, List<? extends PsiElement> result, String propertyName) {
    if (result.size() == 1) {
      return result.get(0);
    }
    else /*if (result.size() > 1)*/ {
      return new PropertyForRename(result, propertyName, element.getManager());
    }
  }

  @Override
  public String getActionTitle() {
    return GroovyRefactoringBundle.message("rename.groovy.property");
  }
}
