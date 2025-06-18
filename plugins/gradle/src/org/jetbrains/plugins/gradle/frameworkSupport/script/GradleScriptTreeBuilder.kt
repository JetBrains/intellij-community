// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.BlockElement

interface GradleScriptTreeBuilder : GradleScriptElementBuilder {

  fun join(builder: GradleScriptTreeBuilder): BlockElement

  fun addElement(statement: Statement): GradleScriptTreeBuilder

  fun addElements(block: BlockElement): GradleScriptTreeBuilder

  fun addNonExistedElements(block: BlockElement): GradleScriptTreeBuilder

  fun generate(): BlockElement

  companion object {

    internal fun create(): GradleScriptTreeBuilder =
      GradleScriptTreeBuilderImpl()

    fun tree(configure: GradleScriptTreeBuilder.() -> Unit): BlockElement =
      create().apply(configure).generate()
  }
}