// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.laf

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ExperimentalUIImpl
import org.junit.jupiter.api.Test

/**
 * @author Konstantin Bulenkov
 */
class ExpUIIconsMappingTest {
  @Test
  internal fun testMappings() {
    val mappings = ExperimentalUIImpl.loadIconMappingsImpl()
    mappings.forEach { (expUI, oldUI) ->
      listOf(expUI, oldUI).forEach {
        if (!(it.endsWith(".svg") || it.endsWith(".png"))) {
          error("Path should end with .svg or .png '$it'")
        }
        if (IconLoader.findIcon(it, AllIcons::class.java)!!.iconHeight == 1) {
          //todo[kb] support classloaders and rewrite to assert
          println("$it is not found")
        }
      }
    }
  }
}