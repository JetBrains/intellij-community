/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ModuleRootModificationUtil {
  public static void addModuleLibrary(Module module, String libName, List<String> classesRoots, List<String> sourceRoots) {
    addModuleLibrary(module, libName, classesRoots, sourceRoots, DependencyScope.COMPILE);
  }

  public static void addModuleLibrary(Module module, String libName, List<String> classesRoots, List<String> sourceRoots,
                                      final DependencyScope scope) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final Library library = model.getModuleLibraryTable().createLibrary(libName);
    final Library.ModifiableModel libraryModel = library.getModifiableModel();
    for (String root : classesRoots) {
      libraryModel.addRoot(root, OrderRootType.CLASSES);
    }
    for (String root : sourceRoots) {
      libraryModel.addRoot(root, OrderRootType.SOURCES);
    }
    model.findLibraryOrderEntry(library).setScope(scope);
    doWriteAction(new Runnable() {
      @Override
      public void run() {
        libraryModel.commit();
        model.commit();
      }
    });
  }

  private static void doWriteAction(final Runnable action) {
    final Application application = ApplicationManager.getApplication();
    application.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        application.runWriteAction(action);
      }
    }, application.getDefaultModalityState());
  }

  public static void addModuleLibrary(Module module, String classesRootUrl) {
    addModuleLibrary(module, null, Collections.singletonList(classesRootUrl), Collections.<String>emptyList());
  }

  public static void addDependency(Module module, Library library) {
    addDependency(module, library, DependencyScope.COMPILE, false);
  }

  public static void addDependency(Module module, Library library, final DependencyScope scope, final boolean exported) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final LibraryOrderEntry entry = model.addLibraryEntry(library);
    entry.setExported(exported);
    entry.setScope(scope);
    doCommit(model);
  }

  public static void setModuleSdk(Module module, @Nullable Sdk sdk) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    model.setSdk(sdk);
    doCommit(model);
  }

  public static void setSdkInherited(Module module) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    model.inheritSdk();
    doCommit(model);
  }

  public static void addDependency(final Module from, final Module to) {
    addDependency(from, to, DependencyScope.COMPILE, false);
  }

  public static void addDependency(final Module from, final Module to, final DependencyScope scope, final boolean exported) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(from).getModifiableModel();
    final ModuleOrderEntry entry = model.addModuleOrderEntry(to);
    entry.setScope(scope);
    entry.setExported(exported);
    doCommit(model);
  }

  private static void doCommit(final ModifiableRootModel model) {
    doWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
      }
    });
  }
}
