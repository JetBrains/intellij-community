// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.convertToStatic;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.HashSet;
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

    new ConvertToStaticProcessor(project, files.toArray(GroovyFile.EMPTY_ARRAY)).run();
  }

  public static Set<GroovyFile> collectFilesForProcessing(@NotNull PsiElement[] elements) {
    Set<GroovyFile> files = new HashSet<>();
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
            if (!file.isDirectory() && index.isInSourceContent(file) && FileTypeRegistry.getInstance().isFileOfType(file, GroovyFileType.GROOVY_FILE_TYPE)) {
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
