// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.codeInspection.ex.InspectionElementsMerger

class JUnitMixedFrameworkInspectionMerger : InspectionElementsMerger() {
  override fun getMergedToolName(): String = "JUnitMixedFramework"

  override fun getSourceToolNames(): Array<String> = arrayOf(
    "JUnit4AnnotatedMethodInJUnit3TestCase",
  )

  override fun getSuppressIds(): Array<String> = arrayOf(
    "JUnit4AnnotatedMethodInJUnit3TestCase",
  )
}