// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.idea.Java11ShimImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.util.Java11Shim
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly

@TestOnly
@Internal
var isTestEnvironmentInitialized = false
  private set

/**
 * Must be called in the very beginning before test initialization happens.
 */
@TestOnly
@Synchronized
@Internal
fun initializeTestEnvironment() {
  if (isTestEnvironmentInitialized) {
    return
  }
  IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(true) // from UsefulTestCase
  Logger.setFactory(TestLoggerFactory::class.java) // from UsefulTestCase
  // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
  System.setProperty("apple.awt.UIElement", "true") // from UsefulTestCase
  Java11Shim.INSTANCE = Java11ShimImpl() // from TestApplicationManager
  ExtensionNotApplicableException.useFactoryWithStacktrace() // from TestApplicationManager
  StartUpMeasurer.disable() // from TestApplicationManager
  PlatformPrefix.autodetectPlatformPrefix() // from TestApplicationManager
  isTestEnvironmentInitialized = true
}
