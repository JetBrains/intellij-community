// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge

import com.intellij.configurationStore.serialize
import com.intellij.model.SideEffectGuard
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.DummyVersionedEntityStorage
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageOnBuilder

//todo restore internal visibility for members of this class after other classes will be moved to this module
abstract class LegacyBridgeModifiableBase(val diff: WorkspaceEntityStorageBuilder, cacheStorageResult: Boolean) {
  init {
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.PROJECT_MODEL)
  }

  val entityStorageOnDiff = if (cacheStorageResult) VersionedEntityStorageOnBuilder(diff)
  else DummyVersionedEntityStorage(diff)

  private var committedOrDisposed = false

  protected var modelIsCommittedOrDisposed
    get() = committedOrDisposed
    set(value) {
      if (!value) error("Only 'true' value is accepted here")
      committedOrDisposed = true
    }

  fun assertModelIsLive() {
    if (committedOrDisposed) {
      error("${javaClass.simpleName} was already committed or disposed")
    }
  }

  companion object {
    // TODO Some common mechanics?
    @JvmStatic
    val assertChangesApplied
      get() = true

    fun serializeComponentAsString(rootElementName: String, component: PersistentStateComponent<*>?): String? {
      val state = component?.state ?: return null
      val propertiesElement = serialize(state) ?: return null
      propertiesElement.name = rootElementName
      return JDOMUtil.writeElement(propertiesElement)
    }
  }
}
