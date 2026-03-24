// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tests

import com.intellij.openapi.application.Application
import com.intellij.remoteDev.tests.impl.DistributedTestHost

val Application.isDistributedTestMode by lazy {
  DistributedTestHost.getDistributedTestPort() != null
}