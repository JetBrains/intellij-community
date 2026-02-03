// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation

import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.codeInsight.daemon.LineMarkerInfo.LineMarkerGutterIconRenderer
import com.intellij.codeInsight.navigation.NavigationGutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.presentation.java.SymbolPresentationUtil
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import javax.swing.Icon

object DevKitGutterTargetsChecker {
  @JvmStatic
  fun checkGutterTargets(
    gutterMark: GutterMark?,
    tooltip: String,
    icon: Icon,
    targetTextMapper: (PsiElement) -> String,
    vararg expectedTargets: String
  ) {
    TestCase.assertNotNull("gutterMark expected to be not null", gutterMark)
    TestCase.assertEquals(tooltip, gutterMark!!.getTooltipText())
    TestCase.assertEquals(icon, gutterMark.getIcon())

    val targetElements: MutableCollection<PsiElement>
    if (gutterMark is LineMarkerGutterIconRenderer<*>) {
      val renderer = UsefulTestCase.assertInstanceOf(gutterMark, LineMarkerGutterIconRenderer::class.java)
      val lineMarkerInfo = renderer.getLineMarkerInfo()
      val handler = lineMarkerInfo.getNavigationHandler()

      if (handler is NavigationGutterIconRenderer) {
        targetElements = handler.getTargetElements()
      }
      else {
        throw IllegalArgumentException("$handler: handler not supported")
      }
    }
    else {
      throw IllegalArgumentException("${gutterMark.javaClass}: gutter not supported")
    }

    UsefulTestCase.assertSameElements(
      targetElements.map(targetTextMapper),
      *expectedTargets
    )
  }

  @JvmStatic
  fun checkGutterTargets(gutterMark: GutterMark?, tooltip: String, icon: Icon, vararg expectedTargets: String) {
    checkGutterTargets(gutterMark, tooltip, icon, { SymbolPresentationUtil.getSymbolPresentableText(it) }, *expectedTargets)
  }
}
