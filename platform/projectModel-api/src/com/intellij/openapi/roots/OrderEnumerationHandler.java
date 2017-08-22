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
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class OrderEnumerationHandler {
  public static final ExtensionPointName<Factory> EP_NAME =
    ExtensionPointName.create("com.intellij.orderEnumerationHandlerFactory");

  public abstract static class Factory {
    /**
     * @deprecated this method isn't called anymore, override {@link #isApplicable(Module)} instead
     */
    public boolean isApplicable(@NotNull Project project) {
      return true;
    }

    public abstract boolean isApplicable(@NotNull Module module);

    public abstract OrderEnumerationHandler createHandler(@NotNull Module module);
  }

  public enum AddDependencyType {ADD, DO_NOT_ADD, DEFAULT}

  @NotNull
  public AddDependencyType shouldAddDependency(@NotNull OrderEntry orderEntry,
                                               @NotNull OrderEnumeratorSettings settings) {
    return AddDependencyType.DEFAULT;
  }

  public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
    return false;
  }

  public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
    return true;
  }

  public boolean shouldProcessDependenciesRecursively() {
    return true;
  }

  public boolean addCustomRootsForLibrary(@NotNull OrderEntry forOrderEntry,
                                          @NotNull OrderRootType type,
                                          @NotNull Collection<String> urls) {
    return false;
  }

  public boolean addCustomModuleRoots(@NotNull OrderRootType type,
                                      @NotNull ModuleRootModel rootModel,
                                      @NotNull Collection<String> result,
                                      boolean includeProduction,
                                      boolean includeTests) {
    return false;
  }
}
