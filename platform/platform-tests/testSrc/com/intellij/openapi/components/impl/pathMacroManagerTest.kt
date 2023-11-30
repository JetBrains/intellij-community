// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl

import com.intellij.application.options.PathMacrosImpl
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.util.SystemProperties
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.junit.Rule
import org.junit.Test

class LightPathMacroManagerTest {
  @get: Rule
  val app: ApplicationRule = ApplicationRule()

  @Test
  fun systemOverridesUser() {
    val macros = PathMacrosImpl()
    val userHome = FileUtil.toSystemIndependentName(SystemProperties.getUserHome())
    macros.setMacro("foo", userHome)

    val manager = PathMacroManager(macros)
    assertThat(manager.collapsePath(userHome)).isEqualTo("$${PathMacroUtil.USER_HOME_NAME}$")
  }
}