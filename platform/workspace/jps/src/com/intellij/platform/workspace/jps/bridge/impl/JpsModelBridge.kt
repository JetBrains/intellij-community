// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl

import com.intellij.platform.workspace.storage.EntityStorage
import org.jetbrains.jps.model.JpsModel

internal class JpsModelBridge(
  projectStorage: EntityStorage, 
  globalStorage: EntityStorage, 
  projectAdditionalData: JpsProjectAdditionalData,
  ) : JpsModel {
    
  private val project: JpsProjectBridge = JpsProjectBridge(this, projectStorage, projectAdditionalData);
  private val global: JpsGlobalBridge = JpsGlobalBridge(this, globalStorage);

  override fun getProject(): JpsProjectBridge = project

  override fun getGlobal(): JpsGlobalBridge = global
}