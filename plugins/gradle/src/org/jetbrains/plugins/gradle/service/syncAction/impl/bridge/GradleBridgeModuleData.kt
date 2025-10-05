// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl.bridge

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object GradleBridgeModuleData {

  val KEY: Key<GradleBridgeModuleData> = Key.create(GradleBridgeModuleData::class.java, ProjectKeys.MODULE.processingWeight + 1)
}