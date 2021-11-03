// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.script.KotlinScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import kotlin.apply as applyKt

class KotlinDslGradleBuildScriptBuilder(
  gradleVersion: GradleVersion
) : AbstractGradleBuildScriptBuilder<KotlinDslGradleBuildScriptBuilder>(gradleVersion) {

  override fun apply(action: KotlinDslGradleBuildScriptBuilder.() -> Unit) = applyKt(action)

  override fun generate() = KotlinScriptBuilder().generate(generateTree())

  override fun configureTask(name: String, configure: ScriptTreeBuilder.() -> Unit) =
    withPostfix {
      val block = block(configure)
      if (!block.isEmpty()) {
        call("tasks.getByName<Test>", string("test"), block)
      }
    }
}