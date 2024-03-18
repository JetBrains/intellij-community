// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.execution.junit.codeInspection.JUnit5AssertionsConverterInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel

abstract class JUnit5AssertionsConverterInspectionTestBase : JvmInspectionTestBase() {
  override val inspection = JUnit5AssertionsConverterInspection()

  protected open class JUnitProjectDescriptor(languageLevel: LanguageLevel) : ProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit4Library()
      model.addHamcrestLibrary()
      model.addJUnit5Library()
    }
  }

  override fun getProjectDescriptor() = JUnitProjectDescriptor(LanguageLevel.HIGHEST)
}