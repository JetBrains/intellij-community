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

package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NonClasspathClassFinder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class GradleClassFinder extends NonClasspathClassFinder {

  @NotNull private final GradleInstallationManager myLibraryManager;

  public GradleClassFinder(Project project, @NotNull GradleInstallationManager manager) {
    super(project, true, true);
    myLibraryManager = manager;
  }

  @Override
  protected List<VirtualFile> calcClassRoots() {
    final List<VirtualFile> roots = myLibraryManager.getClassRoots(myProject);
    if (roots != null) {
      return roots;
    }
    return Collections.emptyList();
  }

  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    PsiClass psiClass = null;
    if (StringUtil.containsChar(qualifiedName, '.')) {
      psiClass = super.findClass(qualifiedName, scope);
    }
    else {
      final Set<String> fqnSet = ContainerUtil.set(GroovyFileBase.IMPLICITLY_IMPORTED_PACKAGES);
      ContainerUtil.addAll(fqnSet, GradleDefaultImportContributor.IMPLICIT_GRADLE_PACKAGES);
      for (String implicitPackage : fqnSet) {
        psiClass = super.findClass(implicitPackage + '.' + qualifiedName, scope);
        if (psiClass != null) {
          break;
        }
      }
    }

    return psiClass;
  }
}