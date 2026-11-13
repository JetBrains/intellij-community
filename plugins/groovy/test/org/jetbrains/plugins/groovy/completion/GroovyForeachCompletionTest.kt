// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion

import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.util.groovyFixture
import org.jetbrains.plugins.groovy.util.groovyProjectFixture
import org.junit.jupiter.api.Test

@TestApplication
class GroovyForeachCompletionTest {
  companion object {
    private val project = groovyProjectFixture()
  }

  private val groovy = groovyFixture(project, GroovyProjectDescriptors.GROOVY_5_0, "groovy/completion/foreach")
  private val completion by groovyCompletionFixture(groovy)

  @Test
  fun testValueVariableType() { completion.doHasVariantsTest("int", "final") }

  @Test
  fun testValueVariableTypeWithIndexVariable() { completion.doVariantableTest("int", "final") }

  @Test
  fun testIndexVariableType() { completion.doVariantableTest("int", "final") }

  @Test
  fun testIndexVariableAccess() { completion.doVariantableTest("idx", "identity", "void") }

  @Test
  fun testValueVariableAccess() { completion.doVariantableTest("value", "var", "metaPropertyValues", "findIndexValues", "findIndexValues", "getMetaPropertyValues") }

  @Test
  fun testValueVariableAccessWithIndexVariable() { completion.doVariantableTest("value", "var", "metaPropertyValues", "findIndexValues", "findIndexValues", "getMetaPropertyValues") }
}