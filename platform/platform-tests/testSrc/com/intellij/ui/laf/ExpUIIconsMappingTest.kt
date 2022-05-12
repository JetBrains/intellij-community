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
        assert(IconLoader.findIcon(it, AllIcons::class.java)!!.iconHeight > 1) { "$it is not found" }
      }
    }
  }
}