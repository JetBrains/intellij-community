// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.BlockElement

interface GradleScriptBuilder {

  fun generate(root: BlockElement): String

  companion object {

    private fun create(gradleDsl: GradleDsl): GradleScriptBuilder {
      return when (gradleDsl) {
        GradleDsl.GROOVY -> GroovyDslGradleScriptBuilder()
        GradleDsl.KOTLIN -> KotlinDslGradleScriptBuilder()
      }
    }

    @JvmStatic
    fun script(gradleDsl: GradleDsl, root: BlockElement): String {
      return create(gradleDsl).generate(root)
    }
  }
}