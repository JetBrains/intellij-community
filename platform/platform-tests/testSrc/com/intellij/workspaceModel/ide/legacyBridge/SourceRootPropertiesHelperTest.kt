// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.SourceRootPropertiesHelper
import org.jdom.Element
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SourceRootPropertiesHelperTest {
  @Test
  fun `copy java source root properties`() {
    fun checkCopy(properties: JavaSourceRootProperties) {
      val copy = SourceRootPropertiesHelper.createPropertiesCopy(properties, JavaSourceRootType.SOURCE)
      assertEquals(properties.packagePrefix, copy.packagePrefix)
      assertEquals(properties.isForGeneratedSources, copy.isForGeneratedSources)
      assertEquals(properties.isPackageMatchesDirectory, copy.isPackageMatchesDirectory)
    }
    checkCopy(JavaSourceRootProperties("", false))
    checkCopy(JavaSourceRootProperties("foo.bar", true))
    checkCopy(JavaSourceRootProperties("foo.bar", true, false))
    checkCopy(JavaSourceRootProperties("", false, false))
  }

  @Test
  fun `save java source root properties`() {
    val serializer = SourceRootPropertiesHelper.findSerializer(JavaSourceRootType.SOURCE)!!
    fun save(properties: JavaSourceRootProperties): String {
      val element = Element(JpsModuleRootModelSerializer.SOURCE_FOLDER_TAG)
      serializer.saveProperties(properties, element)
      return JDOMUtil.write(element)
    }
    // The default value 'true' must stay out of an *.iml file. Only 'false' is written.
    assertEquals("""<sourceFolder isTestSource="false" />""", save(JavaSourceRootProperties("", false)))
    assertEquals("""<sourceFolder isTestSource="false" />""", save(JavaSourceRootProperties("", false, true)))
    assertEquals("""<sourceFolder isTestSource="false" packageMatchesDirectory="false" />""",
                 save(JavaSourceRootProperties("", false, false)))
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