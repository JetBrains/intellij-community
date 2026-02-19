package com.intellij.remoteDev.tests

import com.intellij.openapi.application.Application
import com.intellij.remoteDev.tests.impl.DistributedTestHost

val Application.isDistributedTestMode by lazy {
  DistributedTestHost.getDistributedTestPort() != null
}