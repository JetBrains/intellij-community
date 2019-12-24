package com.intellij.workspace.legacyBridge.libraries.libraries

import com.intellij.workspace.api.EntityStoreOnBuilder
import com.intellij.workspace.api.TypedEntityStorage
import com.intellij.workspace.api.TypedEntityStorageBuilder

abstract class LegacyBridgeModifiableBase(protected val diff: TypedEntityStorageBuilder) {
  protected val entityStoreOnDiff = EntityStoreOnBuilder(diff)

  private var committedOrDisposed = false

  protected var modelIsCommittedOrDisposed
    get() = committedOrDisposed
    set(value) {
      if (!value) error("Only 'true' value is accepted here")
      committedOrDisposed = true
    }

  protected fun assertModelIsLive() {
    if (committedOrDisposed) {
      error("${javaClass.simpleName} was already committed or disposed" )
    }
  }

  companion object {
    // TODO Some common mechanics?
    internal val assertChangesApplied
      get() = true
  }
}
