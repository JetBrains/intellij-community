// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.models

import com.intellij.ide.startup.importSettings.TransferableIdeFeatureId
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls

abstract class FeatureInfo(
  @Deprecated("Not used anymore")
  val transferableId: TransferableIdeFeatureId?,
  @NlsSafe val name: String,
  @Nls val hint: String? = null,
  val isHidden: Boolean = false
) {
  override fun equals(other: Any?): Boolean = other is FeatureInfo && name == other.name
  override fun hashCode(): Int = 31 * (31 * name.hashCode() + (hint?.hashCode() ?: 0)) + isHidden.hashCode()
}

class BuiltInFeature(
  transferableId: TransferableIdeFeatureId?,
  @NlsSafe name: String,
  @Nls hint: String? = null,
  isHidden: Boolean = false
) : FeatureInfo(transferableId, name, hint, isHidden)

class PluginFeature(
  transferableId: TransferableIdeFeatureId?,
  val pluginId: String,
  @NlsSafe name: String,
  @Nls hint: String? = null,
  isHidden: Boolean = false
) : FeatureInfo(transferableId, name, hint, isHidden) {
  override fun equals(other: Any?): Boolean = other is PluginFeature && pluginId == other.pluginId
  override fun hashCode(): Int = pluginId.hashCode()
}

object UnknownFeature : FeatureInfo(null, "[UNKNOWN]", isHidden = true)
