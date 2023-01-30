package com.intellij.remoteDev.tests

import com.intellij.openapi.application.Application
import com.intellij.remoteDev.tests.impl.DistributedTestHostBase

val Application.isDistributedTestMode by lazy {
  DistributedTestHostBase.getDistributedTestPort() != null
}