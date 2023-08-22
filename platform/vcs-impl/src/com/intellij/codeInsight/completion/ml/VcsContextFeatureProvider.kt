// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.ml

import com.intellij.openapi.vcs.changes.ChangeListManager
import org.jetbrains.annotations.NotNull

class VcsContextFeatureProvider : ContextFeatureProvider {
  override fun getName(): String  = "vcs"

  override fun calculateFeatures(environment: @NotNull CompletionEnvironment): @NotNull Map<String, MLFeatureValue> {
    val changeListManager = ChangeListManager.getInstance(environment.parameters.position.project)
    val changesCount = changeListManager.allChanges.size
    environment.putUserData(VcsFeatureProvider.changesCountKey, changesCount)
    return mapOf("changes_count" to MLFeatureValue.numerical(changesCount))
  }
}
