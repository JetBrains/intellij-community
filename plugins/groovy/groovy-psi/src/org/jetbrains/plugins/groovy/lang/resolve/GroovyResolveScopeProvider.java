// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.ResolveScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author Max Medvedev
 */
public class GroovyResolveScopeProvider extends ResolveScopeProvider {

  @Override
  public GlobalSearchScope getResolveScope(@NotNull VirtualFile file, @NotNull Project project) {
    FileType fileType = file.getFileType();
    if (!(fileType instanceof LanguageFileType) || ((LanguageFileType)fileType).getLanguage() != GroovyLanguage.INSTANCE) {
      return null;
    }

    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Module module = projectFileIndex.getModuleForFile(file);

    if (module == null) return null; //groovy files are only in modules

    boolean includeTests = projectFileIndex.isInTestSourceContent(file) || !projectFileIndex.isInSourceContent(file);
    final GlobalSearchScope scope;
    if (projectFileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.RESOURCES)) {
      scope = GlobalSearchScope.moduleRuntimeScope(module, includeTests);
    }
    else {
      scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, includeTests);
    }

    final PsiFile psi = PsiManager.getInstance(project).findFile(file);
    if (psi instanceof GroovyFile && ((GroovyFile)psi).isScript()) {
      return GroovyScriptTypeDetector.patchResolveScope((GroovyFile)psi, scope);
    }
    else {
      return scope;
    }
  }
}
