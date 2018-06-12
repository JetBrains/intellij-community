/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.io.File;
import java.util.regex.Pattern;

/**
 * @author ilyas
 */
public abstract class GroovyUtils {

  public static File[] getFilesInDirectoryByPattern(String dirPath, final String patternString) {
    final Pattern pattern = Pattern.compile(patternString);
    return LibrariesUtil.getFilesInDirectoryByPattern(dirPath, pattern);
  }

  @Nullable
  public static GrTypeDefinition getPublicClass(@NotNull Project project, @NotNull VirtualFile file) {
    return getPublicClass(file, PsiManager.getInstance(project));
  }

  @Nullable
  public static GrTypeDefinition getPublicClass(@Nullable VirtualFile virtualFile, @NotNull PsiManager manager) {
    if (virtualFile == null) return null;

    PsiElement psiFile = manager.findFile(virtualFile);
    if (psiFile instanceof PsiCompiledFile) {
      psiFile = psiFile.getNavigationElement();
    }

    if (psiFile instanceof GroovyFile) {
      return getClassDefinition((GroovyFile)psiFile);
    }

    return null;
  }

  @Nullable
  public static GrTypeDefinition getClassDefinition(@NotNull GroovyFile groovyFile) {
    String fileName = groovyFile.getName();
    int idx = fileName.lastIndexOf('.');
    if (idx < 0) return null;

    return getClassDefinition(groovyFile, fileName.substring(0, idx));
  }

  @Nullable
  public static GrTypeDefinition getClassDefinition(@NotNull GroovyFile groovyFile, @NotNull String classSimpleName) {
    for (GrTypeDefinition definition : (groovyFile).getTypeDefinitions()) {
      if (classSimpleName.equals(definition.getName())) {
        return definition;
      }
    }

    return null;
  }
}
