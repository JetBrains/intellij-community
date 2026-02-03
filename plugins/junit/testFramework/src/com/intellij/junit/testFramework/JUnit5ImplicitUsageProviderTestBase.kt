// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.junit.testFramework.JUnitLibrary.JUNIT5
import com.intellij.jvm.analysis.testFramework.JvmImplicitUsageProviderTestBase
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class JUnit5ImplicitUsageProviderTestBase : JvmImplicitUsageProviderTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JUnitProjectDescriptor(LanguageLevel.HIGHEST, JUNIT5)
}