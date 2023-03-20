// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.laf

import com.intellij.ide.ui.IconMapLoader
import com.intellij.openapi.components.service
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

/**
 * @author Konstantin Bulenkov
 */
class ExpUIIconsMappingTest: BasePlatformTestCase() {
  @Test
  internal fun testMappings() {
    val mappings = service<IconMapLoader>().loadIconMapping()
    mappings.forEach { (classLoader, map) ->
      map.forEach { (expUI, oldUI) ->
        listOf(expUI, oldUI).forEach {
          if (!(it.endsWith(".svg") || it.endsWith(".png"))) {
            error("Path should end with .svg or .png '$it'")
          }
          if (IconLoader.findIcon(it, classLoader)!!.iconHeight == 1) {
            println("$it is not found")
          }
        }
      }
    }
  }
}