/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleJdkUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Computable;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

/**
 * User: anna
 * Date: Nov 24, 2004
 */
public class PluginBuildUtil {
  private PluginBuildUtil() {
  }

  @NonNls @Nullable public static String getPluginExPath(Module module) {
    final Sdk jdk = IdeaJdk.findIdeaJdk(ModuleJdkUtil.getJdk(ModuleRootManager.getInstance(module)));
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

  public static void getDependencies(Module module, Set<Module> modules) {
    for (Module dependency : ModuleRootManager.getInstance(module).getDependencies()) {
      if (dependency.getModuleType() == ModuleType.JAVA) {
        if (modules.add(dependency)) {
          getDependencies(dependency, modules);
        }
      }
    }
  }

  public static Module[] getWrongSetDependencies(final Module module) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Module[]>() {
      public Module[] compute() {
        ArrayList<Module> result = new ArrayList<Module>();
        final Module[] projectModules = ModuleManager.getInstance(module.getProject()).getModules();
        for (Module projectModule : projectModules) {
          if (ArrayUtil.find(ModuleRootManager.getInstance(projectModule).getDependencies(), module) > -1) {
            result.add(projectModule);
          }
        }
        return result.toArray(new Module[result.size()]);
      }
    });
  }

  public static void getLibraries(Module module, Set<Library> libs) {
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libEntry = (LibraryOrderEntry)orderEntry;
        Library lib = libEntry.getLibrary();
        if (lib == null) continue;
        libs.add(lib);
      }
    }
  }
}
