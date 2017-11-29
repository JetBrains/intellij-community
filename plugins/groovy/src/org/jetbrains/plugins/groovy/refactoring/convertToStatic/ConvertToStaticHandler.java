/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToStatic;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.Set;

public class ConvertToStaticHandler implements RefactoringActionHandler {

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    invokeInner(project, new PsiElement[]{file});
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    invokeInner(project, elements);
  }

  private static void invokeInner(Project project, PsiElement[] elements) {
    Set<GroovyFile> files = collectFilesForProcessing(elements);

    new ConvertToStaticProcessor(project, files.toArray(new GroovyFile[files.size()])).run();
  }

  public static Set<GroovyFile> collectFilesForProcessing(@NotNull PsiElement[] elements) {
    Set<GroovyFile> files = ContainerUtil.newHashSet();
    for (PsiElement element : elements) {
      PsiFile containingFile = element.getContainingFile();
      if (containingFile instanceof GroovyFile) {
        files.add((GroovyFile)containingFile);
      }
      if (element instanceof PsiDirectory) {
        PsiDirectory directory = (PsiDirectory)element;
        Module module = ModuleUtilCore.findModuleForFile((directory).getVirtualFile(), element.getProject());
        if (module != null) {
          ModuleFileIndex index = ModuleRootManager.getInstance(module).getFileIndex();
          index.iterateContentUnderDirectory(directory.getVirtualFile(), file -> {
            if (!file.isDirectory() && index.isInSourceContent(file) && GroovyFileType.GROOVY_FILE_TYPE == file.getFileType()) {
              PsiFile psiFile = element.getManager().findFile(file);
              if (psiFile instanceof GroovyFile) {
                files.add((GroovyFile)psiFile);
              }
            }
            return true;
          });
        }
      }
    }
    return files;
  }
}
