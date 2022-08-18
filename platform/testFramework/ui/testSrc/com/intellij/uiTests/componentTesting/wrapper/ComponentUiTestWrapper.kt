// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.componentTesting.wrapper

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.uiTests.componentTesting.canvas.ComponentToTest

internal fun <T : Class<out ComponentToTest>> componentUiTest(componentClass: T, test: CommonContainerFixture.() -> Unit) {
  val componentCanonicalName = componentClass.canonicalName
  with(RemoteRobot("http://127.0.0.1:8580")) {
    showComponent(componentCanonicalName)
    val frame = find(CommonContainerFixture::class.java, byXpath("//div[@title='$componentCanonicalName']"))
    try {
      frame.test()
    }
    finally {
      close()
    }
  }
}

private fun RemoteRobot.showComponent(componentCanonicalName: String) = runJs("""
    importPackage(com.intellij.uiTests.componentTesting.canvas)
    ComponentTesting.INSTANCE.show(new ${componentCanonicalName}())
  """)

private fun RemoteRobot.close() = runJs("""
    importPackage(com.intellij.uiTests.componentTesting.canvas)
    ComponentTesting.INSTANCE.close()
  """)