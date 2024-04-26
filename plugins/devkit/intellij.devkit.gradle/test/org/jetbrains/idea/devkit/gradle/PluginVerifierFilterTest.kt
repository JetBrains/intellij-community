// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.execution.filters.Filter
import com.intellij.ide.browsers.OpenUrlHyperlinkInfo
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.ui.JBColor

class PluginVerifierFilterTest : LightJavaCodeInsightFixtureTestCase() {

  fun testCodeLocationLinks() {
    myFixture.addClass("""
      package x.y;
      
      public class Z {
        private void method() {}
      
        private class ZZ {}
      }
    """.trimIndent())

    // field access
    assertCodeLocationLink(
      "        Deprecated field a.b.C : A is accessed in x.y.Z.method() : String. This field will be removed in a future release",
      50, 64,
      "PsiMethod:method")

    // CTOR
    assertCodeLocationLink("        Deprecated constructor a.b.<init>(java.lang.String id) is invoked in x.y.Z.<init>()\n",
                           77, 91,
                           "PsiClass:Z")

    // lambda
    assertCodeLocationLink(
      "        Deprecated class a.B is referenced in x.y.Z.method\$1\$3\$2.invoke(A) : void. This class will be removed in  a future release",
      46, 74,
      "PsiClass:Z")

    // nested class
    assertCodeLocationLink("        Deprecated method a.B.method(A) : B is overridden in class x.y.Z.ZZ\n",
                           67, 75,
                           "PsiClass:ZZ")

    // FQN in params
    assertCodeLocationLink(
      "        Experimental API interface a.b.C is referenced in x.y.Z.method(PsiFile, a.qualified.name) : X. This interface can be changed in a future release leading to incompatibilities",
      58, 97,
      "PsiMethod:method")

    // unresolved method -> fallback class
    assertCodeLocationLink(
      "        Experimental API interface a.b.C is referenced in x.y.Z.xxxxxx(PsiFile, a.qualified.name) : X. This interface can be changed in a future release leading to incompatibilities",
      58, 97,
      "PsiClass:Z")
  }

  fun testFileOrDirectoryLink() {
    assertFileOrDirectoryLink("Verification reports directory: /a/b/dir",
                              32, 40)

    assertFileOrDirectoryLink("2023-09-07T10:37:59 [main] INFO  c.j.p.options.OptionsParser - Reading IDE from /a/b/dir\n",
                              80, 89)

    assertFileOrDirectoryLink("2023-09-07T10:38:02 [main] INFO  verification - Reading plugin to check from /a/b/plugin.zip\n",
                              77, 93)

    assertFileOrDirectoryLink("2023-09-13T17:00:14 [main] INFO  verification - Verification reports for a.b:0.1 saved to /a/b/dir\n",
                              90, 99)
  }

  fun testHighlighting() {
    val text = "Plugin a.b:0.1 against IC-221.6008.13: Compatible. 1 usage of deprecated API"
    val result = getResult(text)

    val resultItem = assertOneElement(result.resultItems)
    assertHighlightingRange(resultItem, text, 0, 38)

    val highlightAttributes = resultItem.highlightAttributes!!
    assertEquals(JBColor.CYAN, highlightAttributes.foregroundColor)
    assertEquals(EffectType.BOLD_DOTTED_LINE, highlightAttributes.effectType)
  }

  fun testColoredWebLinks() {
    assertColoredWebLink("  Plugin can probably be enabled or disabled without IDE restart",
                         2, 64,
                         JBColor.GREEN)

    assertColoredWebLink("  Plugin probably cannot be enabled or disabled without IDE restart:",
                         2, 68,
                         JBColor.RED)
  }

  private fun assertColoredWebLink(text: String, expectedStartOffset: Int, expectedEndOffset: Int, expectedForegroundColor: JBColor) {
    val result = getResult(text)
    val resultItem = assertOneElement(result.resultItems)
    assertHighlightingRange(resultItem, text, expectedStartOffset, expectedEndOffset)

    assertInstanceOf(result.firstHyperlinkInfo, OpenUrlHyperlinkInfo::class.java)

    val highlightAttributes = resultItem.highlightAttributes!!
    assertEquals(expectedForegroundColor, highlightAttributes.foregroundColor)
  }

  private fun assertFileOrDirectoryLink(text: String, expectedStartOffset: Int, expectedEndOffset: Int) {
    val result = getResult(text)
    val resultItem = assertOneElement(result.resultItems)
    assertHighlightingRange(resultItem, text, expectedStartOffset, expectedEndOffset)
  }

  private fun assertCodeLocationLink(text: String, expectedStartOffset: Int, expectedEndOffset: Int, navigationTargetToString: String?) {
    val result = getResult(text)
    val resultItem = assertOneElement(result.resultItems)
    assertHighlightingRange(resultItem, text, expectedStartOffset, expectedEndOffset)

    val hyperlinkInfo = result.firstHyperlinkInfo
    val codeLocationHyperlinkInfo = assertInstanceOf(hyperlinkInfo,
                                                     PluginVerifierFilter.CodeLocationHyperlinkInfo::class.java)
    val navigationTarget = codeLocationHyperlinkInfo.getNavigationTarget()
    if (navigationTargetToString != null) {
      assertEquals(navigationTargetToString, navigationTarget.toString())
    }
    else {
      assertNull(navigationTarget)
    }
  }

  private fun assertHighlightingRange(resultItem: Filter.ResultItem, text: String, expectedStartOffset: Int, expectedEndOffset: Int) {
    val highlightedText = text.substring(resultItem.highlightStartOffset, resultItem.highlightEndOffset)
    val expectedHighlightedText = text.substring(expectedStartOffset, expectedEndOffset)
    val assertionMessage = "Highlighted '$highlightedText', but expected '$expectedHighlightedText'"
    assertEquals(assertionMessage, expectedStartOffset, resultItem.highlightStartOffset)
    assertEquals(assertionMessage, expectedEndOffset, resultItem.highlightEndOffset)
  }

  private fun getResult(text: String): Filter.Result {
    val filter: Filter = PluginVerifierFilter(project)
    val result = filter.applyFilter(text, text.length)
    assertNotNull(result)
    return result!!
  }
}