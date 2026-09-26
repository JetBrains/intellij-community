// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

public final class PluginBuildUtil {
  private PluginBuildUtil() {
  }

  public static @NonNls @Nullable String getPluginExPath(Module module) {
    final Sdk jdk = IdeaJdk.findIdeaJdk(ModuleRootManager.getInstance(module).getSdk());
    if (jdk == null) {
      return null;
    }
    String sandboxHome = ((Sandbox)jdk.getSdkAdditionalData()).getSandboxHome();
    if (sandboxHome == null) return null;
    try {
      sandboxHome = new File(sandboxHome).getCanonicalPath();
    }
    catch (IOException e) {
      return null;
    }
    return sandboxHome + File.separator + "plugins" + File.separator + module.getName();
  }

  public static void getDependencies(Module module, final Set<? super Module> modules) {
    productionRuntimeDependencies(module).forEachModule(dep -> {
      if (ModuleType.get(dep) == JavaModuleType.getModuleType() && !modules.contains(dep)) {
        modules.add(dep);
        getDependencies(dep, modules);
      }
      return true;
    });
  }

  public static Module[] getWrongSetDependencies(final Module module) {
    return ReadAction.compute(() -> {
      ArrayList<Module> result = new ArrayList<>();
      final Module[] projectModules = ModuleManager.getInstance(module.getProject()).getModules();
      for (Module projectModule : projectModules) {
        if (ArrayUtil.find(ModuleRootManager.getInstance(projectModule).getDependencies(), module) > -1) {
          result.add(projectModule);
        }
      }
      return result.toArray(Module.EMPTY_ARRAY);
    });
  }

  public static void getLibraries(Module module, final Set<? super Library> libs) {
    productionRuntimeDependencies(module).forEachLibrary(library -> {
      libs.add(library);
      return true;
    });
  }

  private static OrderEnumerator productionRuntimeDependencies(Module module) {
    return OrderEnumerator.orderEntries(module).productionOnly().runtimeOnly();
  }
}
