// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.EmptyConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public final class ModuleRootModificationUtil {
  public static void addContentRoot(@NotNull Module module, @NotNull String path) {
    updateModel(module, model -> model.addContentEntry(VfsUtilCore.pathToUrl(path)));
  }

  public static void addContentRoot(@NotNull Module module, @NotNull VirtualFile path) {
    updateModel(module, model -> model.addContentEntry(path));
  }

  public static void addModuleLibrary(@NotNull Module module,
                                      @Nullable String libName,
                                      @NotNull List<String> classesRootUrls,
                                      @NotNull List<String> sourceRootUrls) {
    addModuleLibrary(module, libName, classesRootUrls, sourceRootUrls, DependencyScope.COMPILE);
  }

  public static void addModuleLibrary(@NotNull Module module,
                                      @Nullable String libName,
                                      @NotNull List<String> classesRootUrls,
                                      @NotNull List<String> sourceRootUrls,
                                      @NotNull DependencyScope scope) {
    addModuleLibrary(module, libName, classesRootUrls, sourceRootUrls, Collections.emptyList(), scope);
  }

  public static void addModuleLibrary(@NotNull Module module,
                                      @Nullable String libName,
                                      @NotNull List<String> classesRootUrls,
                                      @NotNull List<String> sourceRootUrls,
                                      @NotNull List<String> excludedRootUrls,
                                      @NotNull DependencyScope scope) {
    addModuleLibrary(module, libName, classesRootUrls, sourceRootUrls, excludedRootUrls, scope, false);
  }

  public static void addModuleLibrary(@NotNull Module module,
                                      @Nullable String libName,
                                      @NotNull List<String> classesRootUrls,
                                      @NotNull List<String> sourceRootUrls,
                                      @NotNull List<String> excludedRootUrls,
                                      @NotNull DependencyScope scope,
                                      boolean exported) {
    addModuleLibrary(module, libName, classesRootUrls, sourceRootUrls, excludedRootUrls, scope, exported, EmptyConsumer.getInstance());
  }

  public static void addModuleLibrary(@NotNull Module module,
                                      @Nullable String libName,
                                      @NotNull List<String> classesRootUrls,
                                      @NotNull List<String> sourceRootUrls,
                                      @NotNull List<String> excludedRootUrls,
                                      @NotNull DependencyScope scope,
                                      boolean exported,
                                      Consumer<? super LibraryEx.ModifiableModelEx> postProcessor) {
    updateModel(module, model -> {
      LibraryEx library = (LibraryEx)model.getModuleLibraryTable().createLibrary(libName);
      LibraryEx.ModifiableModelEx libraryModel = library.getModifiableModel();

      for (String rootUrl : classesRootUrls) {
        libraryModel.addRoot(rootUrl, OrderRootType.CLASSES);
      }
      for (String rootUrl : sourceRootUrls) {
        libraryModel.addRoot(rootUrl, OrderRootType.SOURCES);
      }
      for (String excludedUrl : excludedRootUrls) {
        libraryModel.addExcludedRoot(excludedUrl);
      }

      LibraryOrderEntry entry = model.findLibraryOrderEntry(library);
      assert entry != null : library;
      entry.setScope(scope);
      entry.setExported(exported);

      postProcessor.consume(libraryModel);

      ApplicationManager.getApplication().invokeAndWait(() -> WriteAction.run(libraryModel::commit));
    });
  }

  public static void addModuleLibrary(@NotNull Module module, @NotNull String classesRootUrl) {
    if (ApplicationManager.getApplication().isUnitTestMode() && classesRootUrl.endsWith(".jar")) {
      assert false : "jar file is expected, local file is used";
    }
    addModuleLibrary(module, null, Collections.singletonList(classesRootUrl), Collections.emptyList());
  }

  public static void addDependency(@NotNull Module module, @NotNull Library library) {
    addDependency(module, library, DependencyScope.COMPILE, false);
  }

  public static void addDependency(@NotNull Module module, @NotNull Library library, @NotNull DependencyScope scope, boolean exported) {
    updateModel(module, model -> {
      LibraryOrderEntry entry = model.addLibraryEntry(library);
      entry.setExported(exported);
      entry.setScope(scope);
    });
  }

  public static void setModuleSdk(@NotNull Module module, @Nullable Sdk sdk) {
    updateModel(module, model -> {
      if (sdk != null && ApplicationManager.getApplication().isUnitTestMode()) {
        //noinspection TestOnlyProblems
        WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(sdk, module.getProject()));
      }
      model.setSdk(sdk);
    });
  }

  public static void setSdkInherited(@NotNull Module module) {
    updateModel(module, model -> model.inheritSdk());
  }

  public static void addDependency(@NotNull Module from, @NotNull Module to) {
    addDependency(from, to, DependencyScope.COMPILE, false);
  }

  public static void addDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope, boolean exported) {
    updateModel(from, model -> {
      ModuleOrderEntry entry = model.addModuleOrderEntry(to);
      entry.setScope(scope);
      entry.setExported(exported);
    });
  }

  public static void updateModel(@NotNull Module module, @NotNull Consumer<? super ModifiableRootModel> task) {
    modifyModel(module, model -> {
      task.consume(model);
      return Boolean.TRUE;
    });
  }

  public static void modifyModel(@NotNull Module module, @NotNull Function<? super ModifiableRootModel, Boolean> modifier) {
    ModifiableRootModel model = ReadAction.compute(() -> ModuleRootManager.getInstance(module).getModifiableModel());
    try {
      if (modifier.apply(model)) {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          if (!module.isDisposed()) {
            WriteAction.run(model::commit);
          }
        });
      }
    }
    finally {
      if (!model.isDisposed()) {
        model.dispose();
      }
    }
  }

  public static void updateExcludedFolders(@NotNull Module module,
                                           @NotNull VirtualFile contentRoot,
                                           @NotNull Collection<String> urlsToUnExclude,
                                           @NotNull Collection<String> urlsToExclude) {
    updateModel(module, modifiableModel -> {
      for (ContentEntry contentEntry : modifiableModel.getContentEntries()) {
        if (contentRoot.equals(contentEntry.getFile())) {
          for (String url : urlsToUnExclude) {
            contentEntry.removeExcludeFolder(url);
          }
          for (String url : urlsToExclude) {
            contentEntry.addExcludeFolder(url);
          }
          break;
        }
      }
    });
  }
}