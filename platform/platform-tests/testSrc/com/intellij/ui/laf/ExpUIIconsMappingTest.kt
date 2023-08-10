// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.laf

import com.intellij.ide.ui.IconMapLoader
import com.intellij.openapi.components.service
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

/**
 * @author Konstantin Bulenkov
 */
@TestApplication
class ExpUIIconsMappingTest {
  @Test
  internal fun testMappings() {
    val mappings = service<IconMapLoader>().loadIconMapping()
    for ((classLoader, map) in mappings) {
      for ((expUI, oldUI) in map) {
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