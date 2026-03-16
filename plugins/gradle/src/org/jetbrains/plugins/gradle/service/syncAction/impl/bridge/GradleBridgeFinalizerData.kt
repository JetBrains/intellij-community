// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl.bridge

import com.intellij.openapi.externalSystem.model.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object GradleBridgeFinalizerData {

  val KEY: Key<GradleBridgeFinalizerData> = Key.create(GradleBridgeFinalizerData::class.java, Int.MAX_VALUE)
}