// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.BlockElement

interface ScriptBuilder {

  fun generate(root: BlockElement): String

  companion object {

    private fun create(gradleDsl: GradleDsl): ScriptBuilder {
      return when (gradleDsl) {
        GradleDsl.GROOVY -> GroovyScriptBuilder()
        GradleDsl.KOTLIN -> KotlinScriptBuilder()
      }
    }

    @JvmStatic
    fun script(gradleDsl: GradleDsl, root: BlockElement): String {
      return create(gradleDsl).generate(root)
    }
  }
}