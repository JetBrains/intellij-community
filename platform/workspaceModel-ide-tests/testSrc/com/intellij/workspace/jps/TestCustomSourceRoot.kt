// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.jps

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.ex.JpsElementTypeBase
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension
import org.jetbrains.jps.model.serialization.module.JpsModuleSourceRootPropertiesSerializer

class TestCustomRootModelSerializerExtension : JpsModelSerializerExtension() {
  override fun getModuleSourceRootPropertiesSerializers(): List<JpsModuleSourceRootPropertiesSerializer<*>> =
    listOf(TestCustomSourceRootPropertiesSerializer(TestCustomSourceRootType.INSTANCE, TestCustomSourceRootType.TYPE_ID))
}

class TestCustomSourceRootType private constructor() : JpsElementTypeBase<TestCustomSourceRootProperties>(), JpsModuleSourceRootType<TestCustomSourceRootProperties> {
  override fun isForTests(): Boolean = false

  override fun createDefaultProperties(): TestCustomSourceRootProperties = TestCustomSourceRootProperties("default properties")

  companion object {
    val INSTANCE = TestCustomSourceRootType()
    const val TYPE_ID = "custom-source-root-type"
  }
}

class TestCustomSourceRootProperties(initialTestString: String?) : JpsElementBase<TestCustomSourceRootProperties>() {
  var testString: String? = initialTestString
    set(value) {
      if (value != field) {
        field = value
        fireElementChanged()
      }
    }

  override fun createCopy(): TestCustomSourceRootProperties {
    return TestCustomSourceRootProperties(testString)
  }

  override fun applyChanges(modified: TestCustomSourceRootProperties) {
    testString = modified.testString
  }
}

class TestCustomSourceRootPropertiesSerializer(
  type: JpsModuleSourceRootType<TestCustomSourceRootProperties>, typeId: String)
  : JpsModuleSourceRootPropertiesSerializer<TestCustomSourceRootProperties>(type, typeId) {

  override fun loadProperties(sourceRootTag: Element): TestCustomSourceRootProperties {
    if (sourceRootTag.getAttributeValue("url") == null) error("url is missing in '${JDOMUtil.writeElement(sourceRootTag)}'")
    if (sourceRootTag.getAttributeValue("type") != typeId) error("expected type '$typeId' in '${JDOMUtil.writeElement(sourceRootTag)}'")

    val testString = sourceRootTag.getAttributeValue("testString")
    return TestCustomSourceRootProperties(testString)
  }

  override fun saveProperties(properties: TestCustomSourceRootProperties, sourceRootTag: Element) {
    val testString = properties.testString

    if (testString != null) {
      sourceRootTag.setAttribute("testString", testString)
    }
  }
}
