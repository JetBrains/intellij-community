// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.icons.AllIcons
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.assertInstanceOf
import com.intellij.ui.icons.getReflectiveIcon
import com.intellij.util.ui.EmptyIcon
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import javax.swing.Icon
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class UtilsTest {

  private val instanceIcon: Icon = EmptyIcon.create(10)

  @Before
  fun before() {
    // AllIcons must not be initialized before IconManager is activated, otherwise its static fields keep dummy icons forever
    TestApplicationManager.getInstance()
  }

  @Test
  fun testCleanupHtml() {
    val testData = mapOf(
      "Hello" to "Hello",
      " Hello " to " Hello ",
      "<html>Hello</html>" to "Hello",
      "   <html> Hello </html>   " to " Hello ",
      "<html>Hello" to "<html>Hello",
    )

    for ((original, expected) in testData) {
      assertEquals(expected, cleanupHtml(original))
    }
  }

  @Test
  fun testToHtmlIcon() {
    val src = "AllIcons.General.Information"

    // Check top level icon as a corner case
    assertEquals("<icon src='AllIcons.Empty'>", toHtmlIcon(AllIcons::Empty))
    assertEquals("<icon src='$src'>", toHtmlIcon(AllIcons.General::Information))
    assertSame(AllIcons.General.Information, getReflectiveIcon(src, AllIcons::class.java.classLoader))

    // only AllIcons properties are supported
    assertFailsWith<IllegalArgumentException> {
      toHtmlIcon(EmptyIcon::ICON_16)
    }
    assertFailsWith<IllegalArgumentException> {
      toHtmlIcon(this::instanceIcon)
    }
    assertFailsWith<IllegalArgumentException> {
      toHtmlIcon(::topLevelIcon)
    }
  }

  /**
   * Checks that the src built by [toHtmlIconSrc] can be resolved back by [getReflectiveIcon] for every [AllIcons] icon
   */
  @Test
  fun testToHtmlIconSrcConsistency() {
    assertEquals("AllIcons.General.Information", toHtmlIconSrc(AllIcons.General::Information))

    val allIcons = mutableListOf<Pair<Field, String>>()
    collectIcons(AllIcons::class.java, allIcons)

    for ((field, src) in allIcons) {
      val icon = field.get(null)
      assertInstanceOf<Icon>(icon)
      assertSame(icon, getReflectiveIcon(src, AllIcons::class.java.classLoader), "Inconsistent icon for src '$src'")
    }
  }
}

private fun collectIcons(clazz: Class<*>, result: MutableList<Pair<Field, String>>) {
  for (field in clazz.declaredFields) {
    if (!Modifier.isStatic(field.modifiers) ||
        !Modifier.isPublic(field.modifiers) ||
        !Icon::class.java.isAssignableFrom(field.type)) {
      continue
    }
    // The same src as toHtmlIconSrc builds: com.intellij.icons.AllIcons$General + Information -> AllIcons.General.Information
    val src = clazz.name.substring(AllIcons::class.java.packageName.length + 1).replace('$', '.') + '.' + field.name
    result += Pair(field, src)
  }

  for (nestedClass in clazz.declaredClasses) {
    collectIcons(nestedClass, result)
  }
}

private val topLevelIcon: Icon = EmptyIcon.create(10)
