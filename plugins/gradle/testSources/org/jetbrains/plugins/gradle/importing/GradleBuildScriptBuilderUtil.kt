// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GroovyGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.KotlinGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptBuilder
import java.util.function.Consumer


fun GradleImportingTestCase.buildscript(configure: Consumer<GroovyGradleBuildScriptBuilder>) =
  buildscript(configure::accept)

fun GradleImportingTestCase.buildscript(configure: GroovyGradleBuildScriptBuilder.() -> Unit) =
  buildscript(::GroovyGradleBuildScriptBuilder, configure)

fun GradleImportingTestCase.kotlinBuildscript(configure: Consumer<KotlinGradleBuildScriptBuilder>) =
  kotlinBuildscript(configure::accept)

fun GradleImportingTestCase.kotlinBuildscript(configure: KotlinGradleBuildScriptBuilder.() -> Unit) =
  buildscript(::KotlinGradleBuildScriptBuilder, configure)

fun <SB : ScriptBuilder<SB>, BSB : GradleBuildScriptBuilder<SB, BSB>> GradleImportingTestCase.buildscript(
  createBuilder: (GradleVersion) -> BSB,
  configure: Consumer<BSB>
) = buildscript(createBuilder, configure::accept)

fun <SB : ScriptBuilder<SB>, BSB : GradleBuildScriptBuilder<SB, BSB>> GradleImportingTestCase.buildscript(
  createBuilder: (GradleVersion) -> BSB,
  configure: BSB.() -> Unit
) = buildscript(createBuilder(currentGradleVersion), configure)

fun <SB : ScriptBuilder<SB>, BSB : GradleBuildScriptBuilder<SB, BSB>> buildscript(builder: BSB, configure: Consumer<BSB>) =
  buildscript(builder, configure::accept)

fun <SB : ScriptBuilder<SB>, BSB : GradleBuildScriptBuilder<SB, BSB>> buildscript(builder: BSB, configure: BSB.() -> Unit) =
  builder.apply(configure).generate()
