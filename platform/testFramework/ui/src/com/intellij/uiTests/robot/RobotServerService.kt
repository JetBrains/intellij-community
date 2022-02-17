// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.robot

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.remoterobot.RobotServerImpl
import com.intellij.remoterobot.fixtures.dataExtractor.server.TextToKeyCache
import com.intellij.remoterobot.services.IdeRobot
import com.intellij.remoterobot.services.LambdaLoader
import com.intellij.remoterobot.services.js.RhinoJavaScriptExecutor

internal class RobotServerListener : ApplicationInitializedListener {

  override fun componentsInitialized() {
    TextToKeyCache.init(javaClass.classLoader)
    RobotServerImpl(
      serverHost = if (java.lang.Boolean.getBoolean("robot-server.host.public")) "0.0.0.0" else "127.0.0.1",
      serverPort = Integer.getInteger("robot-server.port", 8580),
    ) {
      IdeRobot(
        textToKeyCache = TextToKeyCache,
        jsExecutor = RhinoJavaScriptExecutor(),
        lambdaLoader = LambdaLoader(),
      )
    }.startServer()
  }
}