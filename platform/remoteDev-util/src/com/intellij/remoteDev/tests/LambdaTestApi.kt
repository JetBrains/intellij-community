package com.intellij.remoteDev.tests

import com.intellij.openapi.application.Application
import com.intellij.remoteDev.tests.impl.LambdaTestHost

val Application.isLambdaTestMode by lazy {
  LambdaTestHost.getLambdaTestPort() != null
}