// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Represents additional debug information associated with search results or items in Search Everywhere.
 * It's supposed to be rendered in the "UI Inspector" dialog.
 *
 * See [com.intellij.internal.inspector.UiInspectorContextProvider]
 */
@ApiStatus.Experimental
@Serializable
sealed interface SeUiInspectorInfo {
  val properties: List<SePropertyBean>
}

@ApiStatus.Experimental
@Serializable
data class SePropertyBean(
  val propertyName: @NlsSafe String,
  val propertyValue: @NlsSafe String?,
  val isChanged: Boolean,
)

/**
 * Builder for [SeExtendedInfo]
 */
@ApiStatus.Experimental
class SeUiInspectorInfoBuilder {
  val properties: MutableList<SePropertyBean> = mutableListOf()

  fun addProperty(property: SePropertyBean?): SeUiInspectorInfoBuilder {
    if (property != null) this.properties += property
    return this
  }

  fun withProperties(properties: List<SePropertyBean>): SeUiInspectorInfoBuilder {
    this.properties.clear()
    this.properties += properties
    return this
  }

  fun build(): SeUiInspectorInfo = SeUiInspectorInfoImpl(properties.toList())
}

@ApiStatus.Internal
@Serializable
private class SeUiInspectorInfoImpl(
  override val properties: List<SePropertyBean>,
) : SeUiInspectorInfo {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SeUiInspectorInfoImpl) return false

    return properties == other.properties
  }

  override fun hashCode(): Int {
    return properties.hashCode()
  }
}
