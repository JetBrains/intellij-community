package com.intellij.workspace.legacyBridge.libraries.libraries

import com.intellij.configurationStore.serialize
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspace.api.*

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

  internal fun serializeComponentAsString(rootElementName: String, component: PersistentStateComponent<*>?) : String? {
    val state = component?.state ?: return null
    val propertiesElement = serialize(state) ?: return null
    propertiesElement.name = rootElementName
    return JDOMUtil.writeElement(propertiesElement)
  }

  companion object {
    // TODO Some common mechanics?
    internal val assertChangesApplied
      get() = true
  }
}
