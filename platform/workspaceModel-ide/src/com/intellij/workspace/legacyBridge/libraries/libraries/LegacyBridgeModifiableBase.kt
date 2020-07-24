package com.intellij.workspace.legacyBridge.libraries.libraries

import com.intellij.configurationStore.serialize
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspace.api.*

abstract class LegacyBridgeModifiableBase(internal val diff: TypedEntityStorageBuilder) {
  internal val entityStoreOnDiff = EntityStoreOnBuilder(diff)

  private var committedOrDisposed = false

  protected var modelIsCommittedOrDisposed
    get() = committedOrDisposed
    set(value) {
      if (!value) error("Only 'true' value is accepted here")
      committedOrDisposed = true
    }

  internal fun assertModelIsLive() {
    if (committedOrDisposed) {
      error("${javaClass.simpleName} was already committed or disposed" )
    }
  }

  companion object {
    // TODO Some common mechanics?
    internal val assertChangesApplied
      get() = true

    internal fun serializeComponentAsString(rootElementName: String, component: PersistentStateComponent<*>?): String? {
      val state = component?.state ?: return null
      val propertiesElement = serialize(state) ?: return null
      propertiesElement.name = rootElementName
      return JDOMUtil.writeElement(propertiesElement)
    }
  }
}
