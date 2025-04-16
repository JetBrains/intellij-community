// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.headers

import com.intellij.editorconfig.common.syntax.psi.impl.EditorConfigElementFactory
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.editorconfig.language.util.isSubcaseOf
import org.junit.Test

class EditorConfigGlobUtilTest : BasePlatformTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testSrc/org/editorconfig/language/globs/"

  // ---- ---- complex tests ---- ----

  @Test
  fun `test work on examples with poor codestyle`() =
    testHeaders("[*.kt]", "[*{.kt, .kts}]")

  // ---- ---- inter-type comparison ---- ----

  @Test
  fun `test that pattern can be subcase of itself`() =
    testHeaders("[hello]", "[hello]")

  @Test
  fun `test that pattern enumeration can be subcase of itself`() =
    testHeaders("[{one, two}]", "[{one, three, two}]")

  @Test
  fun `test that charclass can be subcase of itself`() =
    testHeaders("[[abc]]", "[[abcd]]")

  // ---- ---- cross-type comparison---- ----

  @Test
  fun `test that pattern can be subcase of charclass`() =
    testHeaders("[x]", "[[xyz]]")

  @Test
  fun `test that pattern can be subcase of pattern enumeration`() =
    testHeaders("[hello]", "[{hello, world}]")

  @Test
  fun `test that charclass can be subcase of pattern`() =
    testHeaders("[[a]]", "[a]")

  @Test
  fun `test that charclass can be subcase of pattern enumeration`() =
    testHeaders("[[abc]]", "[{a,b,c,d}]")

  @Test
  fun `test that pattern enumeration can be subcase of charclass`() =
    testHeaders("[{a,b}]", "[[abc]]")

  // ---- ---- asterisk tests ---- ----

  @Test
  fun `test that pattern can be subcase of asterisk`() =
    testHeaders("[hello]", "[*]")

  @Test
  fun `test that charclass can be subcase of asterisk`() =
    testHeaders("[[abc]]", "[*]")

  @Test
  fun `test that pattern enumeration can be subcase of asterisk`() =
    testHeaders("[{hello, world}]", "[*]")

  private fun testHeaders(subcase: String, general: String, expected: Boolean = true) {
    val factory = EditorConfigElementFactory.getInstance(project)
    val subcaseSection = factory.createSection(subcase)
    val generalSection = factory.createSection(general)
    assertEquals(expected, subcaseSection.header.isSubcaseOf(generalSection.header))
  }
}
