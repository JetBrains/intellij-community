// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.SourceRootPropertiesHelper
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SourceRootPropertiesHelperTest {
  @Test
  fun `copy java source root properties`() {
    fun checkCopy(properties: JavaSourceRootProperties) {
      val copy = SourceRootPropertiesHelper.createPropertiesCopy(properties, JavaSourceRootType.SOURCE)
      assertEquals(properties.packagePrefix, copy.packagePrefix)
      assertEquals(properties.isForGeneratedSources, copy.isForGeneratedSources)
    }
    checkCopy(JavaSourceRootProperties("", false))
    checkCopy(JavaSourceRootProperties("foo.bar", true))
  }
  
  @Test
  fun `copy java resource root properties`() {
    fun checkCopy(properties: JavaResourceRootProperties) {
      val copy = SourceRootPropertiesHelper.createPropertiesCopy(properties, JavaResourceRootType.RESOURCE)
      assertEquals(properties.relativeOutputPath, copy.relativeOutputPath)
      assertEquals(properties.isForGeneratedSources, copy.isForGeneratedSources)
    }
    checkCopy(JavaResourceRootProperties("", false))
    checkCopy(JavaResourceRootProperties("foo/bar", true))
  }
}