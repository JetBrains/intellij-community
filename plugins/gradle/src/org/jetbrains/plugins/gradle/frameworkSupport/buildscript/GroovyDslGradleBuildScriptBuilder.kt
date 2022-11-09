// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.GroovyScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import kotlin.apply as applyKt

@ApiStatus.NonExtendable
abstract class GroovyDslGradleBuildScriptBuilder<BSB : GroovyDslGradleBuildScriptBuilder<BSB>>(
  gradleVersion: GradleVersion
) : AbstractGradleBuildScriptBuilder<BSB>(gradleVersion) {

  override fun configureTask(name: String, configure: ScriptTreeBuilder.() -> Unit) =
    withPostfix { callIfNotEmpty(name, configure) }

  override fun generate() = GroovyScriptBuilder().generate(generateTree())

  private class Impl(gradleVersion: GradleVersion) : GroovyDslGradleBuildScriptBuilder<Impl>(gradleVersion) {
    override fun apply(action: Impl.() -> Unit) = applyKt(action)
  }

  companion object {
    @JvmStatic
    fun create(gradleVersion: GradleVersion): GroovyDslGradleBuildScriptBuilder<*> = Impl(gradleVersion)
  }
}