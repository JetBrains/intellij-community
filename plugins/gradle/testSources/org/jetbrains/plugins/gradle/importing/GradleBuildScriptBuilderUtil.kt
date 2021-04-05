// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GroovyGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.KotlinGradleBuildScriptBuilder


fun GradleImportingTestCase.buildscript(configure: GroovyGradleBuildScriptBuilder.() -> Unit) =
  buildscript(currentGradleVersion, configure)

fun GradleImportingTestCase.kotlinBuildscript(configure: KotlinGradleBuildScriptBuilder.() -> Unit) =
  kotlinBuildscript(currentGradleVersion, configure)

fun buildscript(gradleVersion: GradleVersion, configure: GroovyGradleBuildScriptBuilder.() -> Unit) =
  buildscript(GroovyGradleBuildScriptBuilder(gradleVersion), configure)

fun kotlinBuildscript(gradleVersion: GradleVersion, configure: KotlinGradleBuildScriptBuilder.() -> Unit) =
  buildscript(KotlinGradleBuildScriptBuilder(gradleVersion), configure)

fun <BSB : GradleBuildScriptBuilder<BSB>> buildscript(builder: BSB, configure: BSB.() -> Unit) =
  builder.apply(configure).generate()
