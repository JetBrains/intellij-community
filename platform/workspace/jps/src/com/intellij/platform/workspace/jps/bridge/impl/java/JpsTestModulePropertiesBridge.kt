// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.java

import com.intellij.platform.workspace.jps.bridge.impl.module.JpsModuleBridge
import com.intellij.platform.workspace.jps.bridge.impl.module.JpsModuleReferenceBridge
import com.intellij.platform.workspace.jps.entities.TestModulePropertiesEntity
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.model.module.JpsTestModuleProperties

internal class JpsTestModulePropertiesBridge(private val testModulePropertiesEntity: TestModulePropertiesEntity, parentElement: JpsModuleBridge) 
  : JpsElementBase<JpsTestModulePropertiesBridge>(), JpsTestModuleProperties {
  
  private val reference by lazy(LazyThreadSafetyMode.PUBLICATION) {
    JpsModuleReferenceBridge(testModulePropertiesEntity.productionModuleId.name).also { it.parent = this }
  } 
  private val resolved by lazy(LazyThreadSafetyMode.PUBLICATION) {
    productionModuleReference.resolve()
  }  
  init {
    parent = parentElement
  }

  override fun getProductionModuleReference(): JpsModuleReference = reference

  override fun getProductionModule(): JpsModule? = resolved
}
