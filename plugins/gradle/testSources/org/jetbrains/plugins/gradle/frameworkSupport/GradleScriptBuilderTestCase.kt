// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptTreeBuilder
import org.junit.jupiter.api.Assertions.assertEquals

abstract class GradleScriptBuilderTestCase {

  fun assertScript(
    groovyScript: String,
    kotlinScript: String,
    configure: GradleScriptTreeBuilder.() -> Unit
  ) {
    val tree = GradleScriptTreeBuilder.tree(configure)
    assertEquals(groovyScript, GradleScriptBuilder.script(GradleDsl.GROOVY, tree))
    assertEquals(kotlinScript, GradleScriptBuilder.script(GradleDsl.KOTLIN, tree))
  }
}