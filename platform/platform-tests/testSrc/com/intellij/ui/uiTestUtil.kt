// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.ui

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.scale.TestScaleHelper
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.rules.ExternalResource

class RestoreScaleExtension : BeforeAllCallback, AfterAllCallback {
  override fun beforeAll(context: ExtensionContext?) {
    LoadingState.setCurrentState(LoadingState.APP_STARTED)
    IconLoader.activate()
    TestScaleHelper.setState()
  }

  override fun afterAll(context: ExtensionContext?) {
    IconLoader.deactivate()
    TestScaleHelper.restoreState()
  }
}

class DisableSvgCache : BeforeAllCallback, AfterAllCallback {
  private val oldValue = System.getProperty("idea.ui.icons.svg.disk.cache")

  override fun beforeAll(context: ExtensionContext?) {
    System.setProperty("idea.ui.icons.svg.disk.cache", "false")
  }

  override fun afterAll(context: ExtensionContext?) {
    if (oldValue == null) {
      System.clearProperty("idea.ui.icons.svg.disk.cache")
    }
    else {
      System.setProperty("idea.ui.icons.svg.disk.cache", oldValue)
    }
  }
}

open class RestoreScaleRule : ExternalResource() {
  override fun before() {
    IconLoader.activate()
    TestScaleHelper.setState()
  }

  override fun after() {
    IconLoader.deactivate()
    TestScaleHelper.restoreState()
  }
}