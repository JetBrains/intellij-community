/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.references;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ProjectLibraryReference extends RuntimeModuleReferenceBase {
  private final boolean mySuggestModulesWithLibraries;

  public ProjectLibraryReference(@NotNull PsiElement element, boolean suggestModulesWithLibraries) {
    super(element);
    mySuggestModulesWithLibraries = suggestModulesWithLibraries;
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    Project project = myElement.getProject();
    Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraryByName(getValue());
    return library != null ? PomService.convertToPsi(project, new IdeaLibraryPomTarget(project, library)) : null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    Library[] libraries = LibraryTablesRegistrar.getInstance().getLibraryTable(myElement.getProject()).getLibraries();
    List<String> names = new ArrayList<String>();
    for (Library library : libraries) {
      names.add(library.getName());
    }
    if (mySuggestModulesWithLibraries) {
      for (Module module : ModuleManager.getInstance(myElement.getProject()).getModules()) {
        if (!OrderEntryUtil.getModuleLibraries(ModuleRootManager.getInstance(module)).isEmpty()) {
          names.add(module.getName() + ".");
        }
      }
    }
    return ArrayUtil.toStringArray(names);
  }
}
