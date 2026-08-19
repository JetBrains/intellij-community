// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
enum class PluginUpdateCandidateDecision {
  AcceptUpdateToHigherVersion,
  AcceptUpdateToLowerVersionForBrokenOrIncompatiblePlugin,
  KeepExistingPlugin
}

internal object PluginUpdateVersionChecker {

  fun determinePluginUpdateDecision(
    updatePluginVersion: String,
    installedPlugin: IdeaPluginDescriptor,
    ideBuildNumber: BuildNumber?,
  ): PluginUpdateCandidateDecision {
    val versionComparison = VersionComparatorUtil.compare(updatePluginVersion, installedPlugin.version)
    if (versionComparison > 0) return PluginUpdateCandidateDecision.AcceptUpdateToHigherVersion
    if (versionComparison == 0) return PluginUpdateCandidateDecision.KeepExistingPlugin
    return if (PluginDownloader.isIncompatibleOrBrokenPlugin(installedPlugin, ideBuildNumber)) {
      PluginUpdateCandidateDecision.AcceptUpdateToLowerVersionForBrokenOrIncompatiblePlugin
    }
    else {
      PluginUpdateCandidateDecision.KeepExistingPlugin
    }
  }
}