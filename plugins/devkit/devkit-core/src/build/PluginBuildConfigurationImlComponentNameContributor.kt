// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.build

import com.intellij.platform.workspace.jps.serialization.CustomImlComponentNameContributor
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration.MODULE_STATE_COMPONENT

internal class PluginBuildConfigurationImlComponentNameContributor : CustomImlComponentNameContributor {
  override val componentName: String = MODULE_STATE_COMPONENT
}
