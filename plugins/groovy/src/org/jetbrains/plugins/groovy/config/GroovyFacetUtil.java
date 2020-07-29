// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;

public final class GroovyFacetUtil {
  public static final String PLUGIN_MODULE_ID = "PLUGIN_MODULE";

  public static boolean isSuitableModule(Module module) {
    if (module == null) return false;
    return isAcceptableModuleType(ModuleType.get(module));
  }

  public static boolean isAcceptableModuleType(ModuleType type) {
    return type instanceof JavaModuleType || PLUGIN_MODULE_ID.equals(type.getId()) || "ANDROID_MODULE".equals(type.getId());
  }
}
