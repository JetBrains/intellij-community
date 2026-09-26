// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tests

import com.intellij.openapi.application.Application
import com.intellij.openapi.components.serviceOrNull
import org.jetbrains.annotations.ApiStatus

val Application.isDistributedTestMode: Boolean
  get() = serviceOrNull<RemoteDevTestModeService>()?.isDistributedTestMode ?: false

val Application.isLambdaTestMode: Boolean
  get() = serviceOrNull<RemoteDevTestModeService>()?.isLambdaTestMode ?: false

@ApiStatus.Internal
interface RemoteDevTestModeService {
  val isDistributedTestMode: Boolean
  val isLambdaTestMode: Boolean
}
