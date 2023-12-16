// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.local

import com.intellij.configurationStore.SaveSessionProducer
import com.intellij.openapi.components.StateStorage

internal class StateStorageBackedByController : StateStorage {
  override fun <T : Any> getState(component: Any?, componentName: String, stateClass: Class<T>, mergeInto: T?, reload: Boolean): T? {
    TODO("Not yet implemented")
  }

  override fun hasState(componentName: String, reloadData: Boolean): Boolean {
    TODO("Not yet implemented")
  }

  override fun createSaveSessionProducer(): SaveSessionProducer? {
    TODO("Not yet implemented")
  }

  override fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<in String>) {
    TODO("Not yet implemented")
  }
}