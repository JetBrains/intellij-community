// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import org.junit.jupiter.api.Assertions.assertEquals

abstract class ScriptBuilderTestCase {

  fun assertScript(
    groovyScript: String,
    kotlinScript: String,
    configure: ScriptTreeBuilder.() -> Unit
  ) {
    val tree = ScriptTreeBuilder.tree(configure)
    assertEquals(groovyScript, ScriptBuilder.script(GradleDsl.GROOVY, tree))
    assertEquals(kotlinScript, ScriptBuilder.script(GradleDsl.KOTLIN, tree))
  }
}