// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit.testFramework

import com.intellij.execution.junit.codeInspection.JUnitIgnoredTestInspection
import com.intellij.junit.testFramework.MavenTestLib.JUNIT4
import com.intellij.junit.testFramework.MavenTestLib.JUNIT5
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.pom.java.LanguageLevel.Companion.HIGHEST
import com.intellij.testFramework.LightProjectDescriptor

private val descriptor = JUnitProjectDescriptor(HIGHEST, JUNIT4, JUNIT5)

abstract class JUnitIgnoredTestInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: JUnitIgnoredTestInspection = JUnitIgnoredTestInspection()
  override fun getProjectDescriptor(): LightProjectDescriptor = descriptor
}