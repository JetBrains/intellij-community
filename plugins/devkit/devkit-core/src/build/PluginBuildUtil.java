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
package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
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

public class PluginBuildUtil {
  private PluginBuildUtil() {
  }

  @NonNls @Nullable public static String getPluginExPath(Module module) {
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
      if (ModuleType.get(dep) == StdModuleTypes.JAVA && !modules.contains(dep)) {
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
