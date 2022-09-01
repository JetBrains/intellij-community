// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.componentTesting.demo

import com.intellij.remoterobot.fixtures.JButtonFixture
import com.intellij.remoterobot.fixtures.JLabelFixture
import com.intellij.remoterobot.fixtures.JTextFieldFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.uiTests.componentTesting.DemoComponentToTest
import com.intellij.uiTests.componentTesting.wrapper.componentUiTest
import org.junit.Test
import java.awt.event.KeyEvent.VK_A
import java.awt.event.KeyEvent.VK_META

internal class DemoTest {

  @Test
  fun checkDemoComponent() = componentUiTest(DemoComponentToTest::class.java) {
    find<JTextFieldFixture>(byXpath("//div[@class='JTextField']")).click()
    keyboard {
      hotKey(VK_META, VK_A)
      enterText("Idea")
    }
    find<JButtonFixture>(byXpath("//div[@class='JButton']")).click()
    assert(find(JLabelFixture::class.java, byXpath("//div[@class='JBLabel']")).hasText("Idea"))
  }
}