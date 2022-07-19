// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.rt.execution.junit.FileComparisonData
import org.opentest4j.AssertionFailedError

class FileComparisonFailedError(override val message : String, 
                                expectedObject : Any, actualObject : Any,
                                private val filePath: String? = null,
                                private val actualFilePath: String? = null) : AssertionFailedError(message, expectedObject, actualObject), FileComparisonData {
  
  override fun getFilePath(): String? = filePath
  override fun getActualFilePath(): String? = actualFilePath
}