// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.module

import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference

internal class JpsModuleReferenceBridge(private val moduleName: String) 
  : JpsElementBase<JpsModuleReferenceBridge>(), JpsModuleReference {
  
  private val resolved by lazy(LazyThreadSafetyMode.PUBLICATION) {
    model?.project?.findModuleByName(moduleName)
  } 
    
  override fun resolve(): JpsModule? = resolved

  override fun getModuleName(): String = moduleName
}
