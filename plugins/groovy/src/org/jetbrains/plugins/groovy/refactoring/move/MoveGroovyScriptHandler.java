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

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesHandlerBase;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;

import java.util.Arrays;

/**
 * @author Maxim.Medvedev
 */
public class MoveGroovyScriptHandler extends MoveClassesOrPackagesHandlerBase {

  @Override
  public PsiElement[] adjustForMove(Project project, PsiElement[] sourceElements, PsiElement targetElement) {
    return super.adjustForMove(project, sourceElements, targetElement);
  }

  @Override
  public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer) {
    for (PsiElement element : elements) {
      if (!canMove(element)) return false;
    }
    return targetContainer == null || isValidTarget(targetContainer, elements);
  }

  private static boolean canMove(PsiElement element) {
    if (!(element instanceof GroovyFile)) return false;

    final GroovyFile file = (GroovyFile)element;
    final VirtualFile vfile = file.getVirtualFile();
    if (vfile == null || !ProjectRootManager.getInstance(element.getProject()).getFileIndex().isInSource(vfile)) {
      return false;
    }

    final PsiClass[] classes = file.getClasses();
    return classes.length > 1 || classes.length == 1 && classes[0] instanceof GroovyScriptClass;
  }

  @Override
  public boolean isValidTarget(PsiElement psiElement, PsiElement[] sources) {
    return isPackageOrDirectory(psiElement);
  }

  @Override
  public boolean tryToMove(PsiElement element, Project project, DataContext dataContext, @Nullable PsiReference reference, Editor editor) {
    if (canMove(element)) {
      doMove(project, new PsiElement[]{element}, LangDataKeys.TARGET_PSI_ELEMENT.getData(dataContext), null);
      return true;
    }
    return false;
  }

  @Override
  public void doMove(final Project project, PsiElement[] elements, PsiElement initialTargetElement, final MoveCallback moveCallback) {
    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(elements), true)) {
      return;
    }

    final String initialTargetPackageName = MoveClassesOrPackagesImpl.getInitialTargetPackageName(initialTargetElement, elements);
    final PsiDirectory initialTargetDirectory = MoveClassesOrPackagesImpl.getInitialTargetDirectory(initialTargetElement, elements);
    final boolean isTargetDirectoryFixed = MoveClassesOrPackagesImpl.getContainerDirectory(initialTargetElement) != null;

    boolean searchTextOccurences = false;
    for (int i = 0; i < elements.length && !searchTextOccurences; i++) {
      PsiElement psiElement = elements[i];
      searchTextOccurences = TextOccurrencesUtil.isSearchTextOccurencesEnabled(psiElement);
    }
    final MoveGroovyScriptDialog moveDialog =
      new MoveGroovyScriptDialog(project, searchTextOccurences, elements, initialTargetElement, moveCallback);

    boolean searchInComments = JavaRefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS;
    boolean searchForTextOccurences = JavaRefactoringSettings.getInstance().MOVE_SEARCH_FOR_TEXT;
    moveDialog.setData(elements, initialTargetPackageName, initialTargetDirectory, isTargetDirectoryFixed, searchInComments,
                       searchForTextOccurences, HelpID.getMoveHelpID(elements[0]));
    moveDialog.show();
  }
}
