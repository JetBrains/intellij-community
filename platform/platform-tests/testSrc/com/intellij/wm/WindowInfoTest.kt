/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.wm

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.WindowInfoImpl
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.jdom.Element
import org.junit.ClassRule
import org.junit.Test
import java.awt.Rectangle

internal class WindowInfoTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Test
  fun test() {
    val a = WindowInfoImpl()
    a.id = "a"
    doSerializerTest("""<window_info id="a" />""", a)

    a.isActive = true
    doSerializerTest("""<window_info active="true" id="a" />""", a)
  }

  @Test
  fun `anchor, isAutoHide`() {
    val a = WindowInfoImpl()
    a.id = "a"
    a.anchor = ToolWindowAnchor.BOTTOM
    a.isAutoHide = false
    doSerializerTest("""<window_info anchor="bottom" id="a" />""", a)

    a.isAutoHide = true
    doSerializerTest("""<window_info anchor="bottom" auto_hide="true" id="a" />""", a)
  }

  @Test
  fun `floatingBounds`() {
    val a = WindowInfoImpl()
    a.id = "a"
    a.floatingBounds = Rectangle(1, 42, 23, 4)
    doSerializerTest("""<window_info x="1" y="42" width="23" height="4" id="a" />""", a)
  }
}

private fun <T : BaseState> doSerializerTest(@Language("XML") expectedText: String, bean: T): T {
  // test deserializer
  val expectedTrimmed = expectedText.trimIndent()
  val element = assertSerializer(bean, expectedTrimmed)

  // test deserializer
  val o = (element ?: Element("state")).deserialize(bean.javaClass)
  assertSerializer(o, expectedTrimmed, "Deserialization failure")
  return o
}

private fun assertSerializer(bean: Any, expected: String, description: String = "Serialization failure"): Element? {
  val element = bean.serialize()
  assertThat(element?.let { JDOMUtil.writeElement(element).trim() }).`as`(description).isEqualTo(expected)
  return element
}