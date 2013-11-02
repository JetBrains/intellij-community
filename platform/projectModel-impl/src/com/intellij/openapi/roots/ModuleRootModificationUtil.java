/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
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

  public static void addModuleLibrary(final Module module,
                                      final String libName,
                                      final List<String> classesRoots,
                                      final List<String> sourceRoots,
                                      final DependencyScope scope) {
    addModuleLibrary(module, libName, classesRoots, sourceRoots, Collections.<String>emptyList(), scope);
  }

  public static void addModuleLibrary(final Module module, final String libName,
                                      final List<String> classesRoots,
                                      final List<String> sourceRoots,
                                      final List<String> excludedRoots,
                                      final DependencyScope scope) {
    addModuleLibrary(module, libName, classesRoots, sourceRoots, excludedRoots, scope, false);
  }
  public static void addModuleLibrary(final Module module, final String libName,
                                      final List<String> classesRoots,
                                      final List<String> sourceRoots,
                                      final List<String> excludedRoots,
                                      final DependencyScope scope, 
                                      final boolean exported) {
    updateModel(module, new Consumer<ModifiableRootModel>() {
      @Override
      public void consume(final ModifiableRootModel model) {
        final LibraryEx library = (LibraryEx)model.getModuleLibraryTable().createLibrary(libName);
        final LibraryEx.ModifiableModelEx libraryModel = library.getModifiableModel();

        for (String root : classesRoots) {
          libraryModel.addRoot(root, OrderRootType.CLASSES);
        }
        for (String root : sourceRoots) {
          libraryModel.addRoot(root, OrderRootType.SOURCES);
        }
        for (String excluded : excludedRoots) {
          libraryModel.addExcludedRoot(excluded);
        }

        LibraryOrderEntry entry = model.findLibraryOrderEntry(library);
        assert entry != null : library;
        entry.setScope(scope);
        entry.setExported(exported);

        doWriteAction(new Runnable() {
          @Override
          public void run() {
            libraryModel.commit();
          }
        });
      }
    });
  }

  public static void addModuleLibrary(Module module, String classesRootUrl) {
    addModuleLibrary(module, null, Collections.singletonList(classesRootUrl), Collections.<String>emptyList());
  }

  public static void addDependency(Module module, Library library) {
    addDependency(module, library, DependencyScope.COMPILE, false);
  }

  public static void addDependency(Module module, final Library library, final DependencyScope scope, final boolean exported) {
    updateModel(module, new Consumer<ModifiableRootModel>() {
      @Override
      public void consume(ModifiableRootModel model) {
        LibraryOrderEntry entry = model.addLibraryEntry(library);
        entry.setExported(exported);
        entry.setScope(scope);
      }
    });
  }

  public static void setModuleSdk(Module module, @Nullable final Sdk sdk) {
    updateModel(module, new Consumer<ModifiableRootModel>() {
      @Override
      public void consume(ModifiableRootModel model) {
        model.setSdk(sdk);
      }
    });
  }

  public static void setSdkInherited(Module module) {
    updateModel(module, new Consumer<ModifiableRootModel>() {
      @Override
      public void consume(ModifiableRootModel model) {
        model.inheritSdk();
      }
    });
  }

  public static void addDependency(final Module from, final Module to) {
    addDependency(from, to, DependencyScope.COMPILE, false);
  }

  public static void addDependency(Module from, final Module to, final DependencyScope scope, final boolean exported) {
    updateModel(from, new Consumer<ModifiableRootModel>() {
      @Override
      public void consume(ModifiableRootModel model) {
        ModuleOrderEntry entry = model.addModuleOrderEntry(to);
        entry.setScope(scope);
        entry.setExported(exported);
      }
    });
  }

  public static void updateModel(@NotNull Module module, @NotNull Consumer<ModifiableRootModel> task) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    try {
      task.consume(model);
      doWriteAction(new Runnable() {
        @Override
        public void run() {
          model.commit();
        }
      });
    }
    catch (RuntimeException e) {
      model.dispose();
      throw e;
    }
    catch (Error e) {
      model.dispose();
      throw e;
    }
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
}
