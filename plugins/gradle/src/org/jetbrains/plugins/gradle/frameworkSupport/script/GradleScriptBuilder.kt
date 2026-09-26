// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.BlockElement

@ApiStatus.NonExtendable
interface GradleScriptBuilder {

  val gradleDsl: GradleDsl

  fun generate(root: BlockElement): String

  companion object {

    @JvmStatic
    fun script(gradleDsl: GradleDsl, root: BlockElement): String {
      return GradleScriptBuilderImpl(gradleDsl).generate(root)
    }
  }
}