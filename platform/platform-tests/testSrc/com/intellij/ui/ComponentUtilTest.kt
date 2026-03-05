// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.awt.Component
import java.awt.Point
import javax.swing.JPanel

@RunWith(JUnit4::class)
internal class ComponentUtilTest : UsefulTestCase() {

  override fun runInDispatchThread(): Boolean = true

  @Test
  fun `convertPoint - to the same component`() {
    val c = JPanel()
    testConvertPoint(c, Point(12, 34), c, Point(12, 34))
  }

  @Test
  fun `convertPoint - to the direct parent, same location`() {
    val c = JPanel()
    val p = JPanel()
    p.add(c)
    c.location = Point(0, 0)
    p.location = Point(30, 30)
    testConvertPoint(c, Point(12, 34), p, Point(12, 34))
  }

  @Test
  fun `convertPoint - to the direct parent, different location`() {
    val c = JPanel()
    val p = JPanel()
    p.add(c)
    c.location = Point(10, 20)
    p.location = Point(30, 40)
    testConvertPoint(c, Point(12, 34), p, Point(22, 54))
  }

  @Test
  fun `convertPoint - to a direct child, different location`() {
    val c = JPanel()
    val p = JPanel()
    p.add(c)
    c.location = Point(10, 20)
    p.location = Point(30, 40)
    testConvertPoint(p, Point(12, 34), c, Point(2, 14))
  }

  @Test
  fun `convertPoint - to an ancestor`() {
    val c = JPanel()
    val p = JPanel()
    val a = JPanel()
    p.add(c)
    a.add(p)
    c.location = Point(10, 20)
    p.location = Point(30, 40)
    a.location = Point(50, 60)
    testConvertPoint(c, Point(12, 34), a, Point(52, 94))
  }

  @Test
  fun `convertPoint - to a descendant`() {
    val c = JPanel()
    val p = JPanel()
    val a = JPanel()
    p.add(c)
    a.add(p)
    c.location = Point(10, 20)
    p.location = Point(30, 40)
    a.location = Point(50, 60)
    testConvertPoint(a, Point(12, 34), c, Point(-28, -26))
  }

  @Test
  fun `convertPoint - independent components`() {
    // Not a "true" test, as getLocationOnScreen() requires the component to be showing.
    // It's more of a "check that delegation to SwingUtilities.convertPoint is not broken" test.
    val c1 = JPanel()
    val c2 = JPanel()
    c1.location = Point(10, 20)
    c2.location = Point(30, 40)
    testConvertPoint(c2, Point(12, 34), c1, Point(32, 54))
  }

  private fun testConvertPoint(source: Component, point: Point, destination: Component, expected: Point) {
    val actual = ComponentUtil.convertPoint(source, point, destination)
    assertThat(actual).isNotSameAs(point)
    assertThat(actual).isEqualTo(expected)
  }
}
