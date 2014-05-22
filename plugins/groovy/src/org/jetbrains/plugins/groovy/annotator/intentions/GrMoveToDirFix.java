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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.CommonBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author Max Medvedev
 */
public class GrMoveToDirFix extends GroovyFix {
  private final String myPackageName;

  public GrMoveToDirFix(String packageName) {
    myPackageName = packageName;
  }

  @NotNull
  @Override
  public String getName() {
    String packName = StringUtil.isEmptyOrSpaces(myPackageName) ? "default package" : myPackageName;
    return GroovyIntentionsBundle.message("move.to.correct.dir", packName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyIntentionsBundle.message("move.to.correct.dir.family.name");
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    PsiFile file = descriptor.getPsiElement().getContainingFile();

    if (!(file instanceof GroovyFile)) return;

    VirtualFile vfile = file.getVirtualFile();
    if (vfile == null) return;

    final Module module = ModuleUtilCore.findModuleForFile(vfile, project);
    if (module == null) return;

    final String packageName = ((GroovyFile)file).getPackageName();
    PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(module, packageName, null, true);
    if (directory == null) return;

    String error = RefactoringMessageUtil.checkCanCreateFile(directory, file.getName());
    if (error != null) {
      Messages.showMessageDialog(project, error, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return;
    }
    new MoveFilesOrDirectoriesProcessor(project, new PsiElement[]{file}, directory, false, false, false, null, null).run();
  }
}
