// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PiJbCentralModelContributor {
  suspend fun listModels(launchMetadata: PiJbCentralLaunchMetadata): List<PiJbCentralModelCandidate>
}

internal suspend fun listPiJbCentralContributorModels(launchMetadata: PiJbCentralLaunchMetadata): List<PiJbCentralModelCandidate> {
  return PI_JBCENTRAL_MODEL_CONTRIBUTOR_EP.extensionList.flatMap { contributor ->
    contributor.listModels(launchMetadata)
  }
}

private val PI_JBCENTRAL_MODEL_CONTRIBUTOR_EP: ExtensionPointName<PiJbCentralModelContributor> =
  ExtensionPointName("com.intellij.agent.workbench.pi.jbCentralModelContributor")
