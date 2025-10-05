// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.jvm.analysis.testFramework.LightJvmCodeInsightFixtureTestCase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel

abstract class JUnitParameterizedSourceCompletionTestBase : LightJvmCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): JUnitProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST)

  protected open class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit5Library()
    }
  }
}