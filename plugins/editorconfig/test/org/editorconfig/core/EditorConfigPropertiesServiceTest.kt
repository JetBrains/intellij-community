// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.core

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.ec4j.core.model.Property
import org.editorconfig.plugincomponents.EditorConfigPropertiesService

class EditorConfigPropertiesServiceTest : BasePlatformTestCase() {
  fun testEmptyValueIsNotFailure() {
    val file = myFixture.configureByFile(".editorconfig")
    lateinit var props: Map<String, Property>
    assertNoThrowable {
      props = EditorConfigPropertiesService.getInstance(myFixture.project).getProperties(file.virtualFile).properties
    }
    assertTrue("key" in props)
    assertTrue(props["key"]!!.sourceValue.isEmpty())
  }

  override fun getBasePath(): String =
    "/plugins/editorconfig/testData/org/editorconfig/core/settingsProviderComponent/" + getTestName(true)

  override fun isCommunity(): Boolean {
    return true
  }
}