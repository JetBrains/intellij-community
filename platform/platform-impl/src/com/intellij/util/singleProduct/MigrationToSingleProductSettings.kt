// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.singleProduct

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@State(
  name = "MigrationToSingleProductSettings",
  storages = [Storage("migrationToSingleProduct.xml")]
)
@Service(Service.Level.APP)
class MigrationToSingleProductSettings
  : SimplePersistentStateComponent<MigrationToSingleProductSettings.State>(State()) {

  class State : BaseState() {
    var afterMigrationDocumentWasShown: Boolean by property(false)
  }

  companion object {
    @JvmStatic
    fun getInstance(): MigrationToSingleProductSettings = service()
  }
}
