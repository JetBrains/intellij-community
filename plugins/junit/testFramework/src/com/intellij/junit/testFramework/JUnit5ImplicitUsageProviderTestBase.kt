// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.jvm.analysis.testFramework.JvmImplicitUsageProviderTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class JUnit5ImplicitUsageProviderTestBase : JvmImplicitUsageProviderTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JUnit5ProjectDescriptor("5.13.4")

  protected open class JUnit5ProjectDescriptor(private val version: String) : ProjectDescriptor(LanguageLevel.HIGHEST) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addJUnit5Library(version)
    }
  }
}