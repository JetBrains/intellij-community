/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author Max Medvedev
 */
public class GrMoveToDirFix implements IntentionAction {
  private String myPackageName;

  public GrMoveToDirFix(String packageName) {
    myPackageName = packageName;
  }

  @NotNull
  @Override
  public String getText() {
    return GroovyIntentionsBundle.message("move.to.correct.dir", myPackageName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyIntentionsBundle.message("move.to.correct.dir.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof GroovyFile)) return false;

    PsiDirectory currentDir = file.getContainingDirectory();


    final VirtualFile sourceRoot = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(file.getVirtualFile());
    if (sourceRoot == null) return false;

    final PsiManager manager = PsiManager.getInstance(project);
    final PsiDirectory targetDir =
      RefactoringUtil.findPackageDirectoryInSourceRoot(new PackageWrapper(manager, myPackageName), sourceRoot);

    return targetDir != null && !manager.areElementsEquivalent(targetDir, currentDir);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!(file instanceof GroovyFile)) return;

    final String packageName = ((GroovyFile)file).getPackageName();
    final Module module = ModuleUtil.findModuleForFile(file.getVirtualFile(), project);
    PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, null, true);

    if (directory == null) {
      return;
    }
    String error = RefactoringMessageUtil.checkCanCreateFile(directory, file.getName());
    if (error != null) {
      Messages.showMessageDialog(project, error, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return;
    }
    new MoveFilesOrDirectoriesProcessor(project, new PsiElement[]{file}, directory, false, false, false, null, null).run();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
