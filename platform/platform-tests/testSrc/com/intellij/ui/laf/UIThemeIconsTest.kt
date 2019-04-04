// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.laf

import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase

/**
 * @author Konstantin Bulenkov
 */
class UIThemeIconsTest: LightPlatformTestCase() {
  fun testComponentIconsLocation() {
    val basePath = "/com/intellij/ide/ui/laf/icons/"
    val paths = arrayOf("${basePath}checkBox.svg",
                       "${basePath}radio.svg")
    val clazz = this.javaClass
    paths.forEach {
      TestCase.assertNotNull("Laf icons are moved from '$basePath'. Please fix PaletteScopeManager.getScopeByURL and the test",
                             clazz.getResourceAsStream(it))
    }
  }
}