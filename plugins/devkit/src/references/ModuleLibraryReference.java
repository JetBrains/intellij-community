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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ModuleLibraryReference extends ModuleResourceReferenceBase {
  public ModuleLibraryReference(@NotNull PsiElement libraryNameElement, @NotNull PsiLiteralExpression moduleNameElement) {
    super(libraryNameElement, moduleNameElement);
  }

  public ModuleLibraryReference(@NotNull PsiElement element, @Nullable IdeaModuleReference moduleReference) {
    super(element, moduleReference);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    Map<String, Library> libraries = getLibrariesMap();
    if (libraries == null) return null;

    Library library = libraries.get(getValue());
    Project project = myElement.getProject();
    return library != null ? PomService.convertToPsi(project, new IdeaLibraryPomTarget(project, library)) : null;
  }

  @Nullable
  private Map<String, Library> getLibrariesMap() {
    Module module = resolveModule();
    if (module == null) return null;
    Map<String, Library> libraries = new HashMap<String, Library>();
    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        Library library = ((LibraryOrderEntry)entry).getLibrary();
        if (library != null && library.getTable() == null) {
          String name = library.getName();
          if (name == null) {
            VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
            if (files.length == 0) continue;
            name = files[0].getName();
          }
          libraries.put(name, library);
        }
      }
    }
    return libraries;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    Map<String, Library> map = getLibrariesMap();
    return map != null ? ArrayUtil.toStringArray(map.keySet()) : ArrayUtil.EMPTY_STRING_ARRAY;
  }
}
