/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.idea.devkit.module;

import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.openapi.module.ModuleType;


public class PluginModuleBuilder extends JavaModuleBuilder{


  public ModuleType getModuleType() {
    return PluginModuleType.getInstance();
  }


}
