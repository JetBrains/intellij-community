// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.laf

import com.intellij.ide.ui.IconMapLoader
import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.icons.findIconByPath
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * @author Konstantin Bulenkov
 */
@TestApplication
class NewUiIconMappingTest {
  @Test
  internal fun testMappings() = runBlocking {
    val mappings = service<IconMapLoader>().doLoadIconMapping()
      for ((classLoader, map) in mappings) {
        for ((expUI, oldUI) in map) {
          for (name in listOf(expUI, oldUI)) {
            if (!name.endsWith(".svg") && !name.endsWith(".png")) {
              error("Path should end with .svg or .png '$name' (expUI=$expUI, oldUI=$oldUI")
            }
            if (findIconByPath(path = name, classLoader = classLoader, cache = null)!!.iconHeight == 1) {
              println("$name is not found")
            }
          }
        }
      }
  }
}