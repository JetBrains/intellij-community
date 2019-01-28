// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.ExtensionsArea
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.LightPlatformTestCase

class LanguageExtensionCacheTest : LightPlatformTestCase() {

  private val myExtensionPointName = "testLangExt"
  private val myExtensionPointXML = """
<extensionPoint qualifiedName="$myExtensionPointName" beanClass="com.intellij.lang.LanguageExtensionPoint">
  <with attribute="implementationClass" implements="java.lang.String"/>
</extensionPoint>
"""

  private val myDescriptor = DefaultPluginDescriptor(PluginId.getId(""), javaClass.classLoader)
  private lateinit var myArea: ExtensionsArea
  private lateinit var myExtension: LanguageExtension<String>

  override fun setUp() {
    super.setUp()
    myArea = Extensions.getRootArea()
    myArea.registerExtensionPoint(myDescriptor, JDOMUtil.load(myExtensionPointXML))
    Disposer.register(testRootDisposable, Disposable {
      myArea.unregisterExtensionPoint(myExtensionPointName)
    })
    myExtension = LanguageExtension(myExtensionPointName, null, testRootDisposable)
  }

  private fun registerExtension(languageID: String, implementationFqn: String) {
    val element = JDOMUtil.load(
      """<extension point="$myExtensionPointName" language="$languageID" implementationClass="$implementationFqn"/>"""
    )
    myArea.registerExtension(myDescriptor, element, null)
  }

  fun `test extensions are cleared when explicit extension is added`() {
    val language = PlainTextLanguage.INSTANCE

    registerExtension(language.id, String::class.java.name)   // emulate registration via plugin.xml
    assertSize(1, myExtension.allForLanguage(language))
    assertEquals("", myExtension.forLanguage(language))       // empty because created with new String(); it is cached within forLanguage()

    myExtension.addExplicitExtension(language, "hello")
    assertSize(2, myExtension.allForLanguage(language))
    assertEquals("hello", myExtension.forLanguage(language))  // explicit extension takes precedence over extension from plugin.xml

    myExtension.removeExplicitExtension(language, "hello")
    assertSize(1, myExtension.allForLanguage(language))
    assertEquals("", myExtension.forLanguage(language))
  }
}
