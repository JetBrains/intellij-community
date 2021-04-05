// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.script.KotlinScriptBuilder
import kotlin.apply as applyKt

class KotlinGradleBuildScriptBuilder(
  gradleVersion: GradleVersion
) : AbstractGradleBuildScriptBuilder<KotlinGradleBuildScriptBuilder>(gradleVersion) {

  override val scriptBuilder = KotlinScriptBuilder()

  override fun apply(action: KotlinGradleBuildScriptBuilder.() -> Unit) = applyKt(action)
}