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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.openapi.impl.JavaRenameRefactoringImpl;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

/**
 * @author ven
 */
public class PropertyRenameHandler implements RenameHandler {
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    return getProperty(dataContext) != null;
  }

  @Nullable
  private static GrField getProperty(DataContext dataContext) {
    final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element instanceof GrField && ((GrField)element).isProperty()) return (GrField)element;
    if (element instanceof GrAccessorMethod) return ((GrAccessorMethod)element).getProperty();
    return null;
  }

  public boolean isRenaming(DataContext dataContext) {
    return getProperty(dataContext) != null;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, @Nullable DataContext dataContext) {
    final GrField property = getProperty(dataContext);
    assert property != null;
    new PropertyRenameDialog(property, editor).show();
  }

  private static class PropertyRenameDialog extends RenameDialog {

    private final GrField myProperty;

    protected PropertyRenameDialog(GrField property, final Editor editor) {
      super(property.getProject(), property, null, editor);
      myProperty = property;
    }

    protected void doAction() {
      final String newName = getNewName();
      final boolean searchInComments = isSearchInComments();
      doRename(newName, searchInComments);
      close(OK_EXIT_CODE);
    }

    private void doRename(String newName, boolean searchInComments) {
      final RenameRefactoring rename = new JavaRenameRefactoringImpl(myProperty.getProject(), myProperty, newName, searchInComments, false);
      rename.setPreviewUsages(isPreviewUsages());
      final PsiMethod setter = myProperty.getSetter();
      if (setter != null && !(setter instanceof GrAccessorMethod)) {
        final String setterName = PropertyUtil.suggestSetterName(newName);
        rename.addElement(setter, setterName);
      }

      rename.run();
    }

    protected boolean areButtonsValid() {
      final String newName = getNewName();
      return super.areButtonsValid() && !GroovyRefactoringUtil.KEYWORDS.contains(newName);
    }

  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, @Nullable DataContext dataContext) {
    final GrField property = getProperty(dataContext);
    if (dataContext == null || property == null) return;
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    new PropertyRenameDialog(property, editor).show();
  }
}
