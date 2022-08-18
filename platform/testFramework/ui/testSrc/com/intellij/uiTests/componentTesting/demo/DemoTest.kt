// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.componentTesting.demo

import com.intellij.remoterobot.fixtures.JLabelFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.uiTests.componentTesting.DemoComponentToTest
import com.intellij.uiTests.componentTesting.wrapper.componentUiTest
import org.junit.Test

internal class DemoTest {

  @Test
  fun checkDemoComponent() = componentUiTest(DemoComponentToTest::class.java) {
    val label = find<JLabelFixture>(byXpath("//div[@class='JBLabel']"))
    assert(label.value == "My test component")
  }
}