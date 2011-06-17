/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.lang.TitledHandler;
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

import static org.jetbrains.plugins.groovy.refactoring.rename.RenamePropertyUtil.askToRenameProperty;

/**
 * @author ven
 */
public class PropertyRenameHandler implements RenameHandler, TitledHandler {
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    final PsiElement element = getElement(dataContext);
    if (element instanceof GrField && ((GrField)element).isProperty()) return true;
    if (element instanceof GrAccessorMethod) return true;
    if (element instanceof GrMethod && GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)element)) return true;
    return false;
  }

  @Nullable
  private static PsiElement getElement(DataContext dataContext) {
    return LangDataKeys.PSI_ELEMENT.getData(dataContext);
  }

  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, @Nullable DataContext dataContext) {
    final PsiElement element = getElement(dataContext);
    invokeInner(project, editor, element);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, @Nullable DataContext dataContext) {
    PsiElement element = elements.length == 1 ? elements[0] : null;
    if (element == null) element = getElement(dataContext);
    Editor editor = dataContext == null ? null : PlatformDataKeys.EDITOR.getData(dataContext);
    invokeInner(project, editor, element);
  }

  private static void invokeInner(Project project, Editor editor, PsiElement element) {
    final Pair<List<? extends PsiElement>, String> pair = askToRenameProperty((PsiMember)element);
    final List<? extends PsiElement> result = pair.getFirst();
    if (result.size() == 0) return;
    if (result.size() == 1) {
      PsiElementRenameHandler.invoke(result.get(0), project, result.get(0), editor);
      return;
    }
    final String propertyName = pair.getSecond();

    PsiElementRenameHandler.invoke(new PropertyForRename(result, propertyName, element.getManager()), project, element, editor);
  }

  @Override
  public String getActionTitle() {
    return GroovyRefactoringBundle.message("rename.groovy.property");
  }
}
