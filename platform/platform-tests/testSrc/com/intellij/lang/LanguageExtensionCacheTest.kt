// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang

import com.intellij.mock.MockLanguageFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.LightPlatformTestCase
import java.util.*

class LanguageExtensionCacheTest : LightPlatformTestCase() {
  private val myExtensionPointName = "testLangExt"
  private val myExtensionPointXML = """
<extensionPoint qualifiedName="$myExtensionPointName" beanClass="com.intellij.lang.LanguageExtensionPoint">
  <with attribute="implementationClass" implements="java.lang.String"/>
</extensionPoint>
"""

  private val descriptor = DefaultPluginDescriptor(PluginId.getId(""), javaClass.classLoader)
  private lateinit var area: ExtensionsAreaImpl
  private lateinit var extension: LanguageExtension<String>
  private val plainTextDialect = object : Language(PlainTextLanguage.INSTANCE, "PlainTextDialect") {
  }


  override fun setUp() {
    super.setUp()
    area = ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl
    area.registerExtensionPoints(descriptor, Collections.singletonList(JDOMUtil.load(myExtensionPointXML)), ApplicationManager.getApplication ())
    Disposer.register(testRootDisposable, Disposable {
      area.unregisterExtensionPoint(myExtensionPointName)
    })
    extension = LanguageExtension(myExtensionPointName, null)

    val plainTextDialectFileType = MockLanguageFileType(plainTextDialect, "xxxx")
    FileTypeManager.getInstance().registerFileType(plainTextDialectFileType)
    Disposer.register(testRootDisposable, Disposable { FileTypeManagerEx.getInstanceEx().unregisterFileType(plainTextDialectFileType) })
  }

  private fun registerExtension(languageID: String, implementationFqn: String) {
    val element = JDOMUtil.load(
      """<extension point="$myExtensionPointName" language="$languageID" implementationClass="$implementationFqn"/>"""
    )
    area.registerExtension(descriptor, element, null)
  }

  fun `test extensions are cleared when explicit extension is added`() {
    val language = PlainTextLanguage.INSTANCE

    registerExtension(language.id, String::class.java.name)   // emulate registration via plugin.xml
    assertSize(1, extension.allForLanguage(language))
    assertEquals("", extension.forLanguage(language))       // empty because created with new String(); it is cached within forLanguage()

    extension.addExplicitExtension(language, "hello")
    assertSize(2, extension.allForLanguage(language))
    assertEquals("hello", extension.forLanguage(language))  // explicit extension takes precedence over extension from plugin.xml

    assertSize(2, extension.allForLanguage(plainTextDialect))

    extension.removeExplicitExtension(language, "hello")
    assertSize(1, extension.allForLanguage(language))
    assertSize(1, extension.allForLanguage(plainTextDialect))
    assertEquals("", extension.forLanguage(language))
  }
}
