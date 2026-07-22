// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tests.impl

import com.intellij.remoteDev.tests.RemoteDevTestModeService

internal class RemoteDevTestModeServiceImpl : RemoteDevTestModeService {
  override val isDistributedTestMode: Boolean
    get() = DistributedTestHost.getDistributedTestPort() != null

  override val isLambdaTestMode: Boolean
    get() = LambdaTestHost.getLambdaTestPort() != null
}
