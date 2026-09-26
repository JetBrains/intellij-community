// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.mock.MockLanguageFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.registerExtension
import com.intellij.util.KeyedLazyInstance

class LanguageExtensionCacheTest : LightPlatformTestCase() {
  @Suppress("UnresolvedPluginConfigReference")
  private val myExtensionPointName = ExtensionPointName<KeyedLazyInstance<String>>("testLangExt")
  @Suppress("UnresolvedPluginConfigReference")
  private val myCompletionExtensionPointName = ExtensionPointName<KeyedLazyInstance<String>>("testCompletionExt")

  private val myExtensionPointXML = """
      <extensionPoint qualifiedName="$myExtensionPointName" beanClass="com.intellij.lang.LanguageExtensionPoint">
        <with attribute="implementationClass" implements="java.lang.String"/>
      </extensionPoint>
      """.trimIndent()
  private val myCompletionExtensionPointXML = """
      <extensionPoint qualifiedName="$myCompletionExtensionPointName" beanClass="com.intellij.lang.LanguageExtensionPoint">
        <with attribute="implementationClass" implements="java.lang.String"/>
      </extensionPoint>
      """.trimIndent()

  private val descriptor = DefaultPluginDescriptor(PluginId.getId(""), javaClass.classLoader)
  private lateinit var area: ExtensionsAreaImpl
  private lateinit var extension: LanguageExtension<String>
  private lateinit var completionExtension: LanguageExtensionWithAny<String>

  override fun setUp() {
    super.setUp()
    area = ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl
    area.registerExtensionPoints(descriptor, listOf(JDOMUtil.load(myExtensionPointXML), JDOMUtil.load(myCompletionExtensionPointXML)))
    Disposer.register(testRootDisposable, Disposable {
      area.unregisterExtensionPoint(myExtensionPointName.name)
      area.unregisterExtensionPoint(myCompletionExtensionPointName.name)
    })
    extension = LanguageExtension(myExtensionPointName, null)
    completionExtension = LanguageExtensionWithAny(myCompletionExtensionPointName.name)
  }

  private fun registerExtension(extensionPointName: ExtensionPointName<KeyedLazyInstance<String>>,
                                languageID: String,
                                implementationFqn: String): Disposable {
    val disposable = Disposer.newDisposable(testRootDisposable, "registerExtension")
    val ep = LanguageExtensionPoint<String>(languageID, implementationFqn, PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!)
    ApplicationManager.getApplication().registerExtension(extensionPointName, ep, disposable)
    return disposable
  }

  fun `test extensions are cleared when explicit extension is added`() {
    val language = PlainTextLanguage.INSTANCE

    val unregisterDialectDisposable = Disposer.newDisposable(testRootDisposable, getTestName(false))
    val plainTextDialect = registerLanguageDialect(unregisterDialectDisposable)

    val extensionRegistrationDisposable = registerExtension(myExtensionPointName, language.id, String::class.java.name)   // emulate registration via plugin.xml
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

    Disposer.dispose(unregisterDialectDisposable)

    Disposer.dispose(extensionRegistrationDisposable)
  }

  private fun registerLanguageDialect(parentDisposable: Disposable): Language {
    val language: Language = object : Language(PlainTextLanguage.INSTANCE, "PlainTextDialect" + getTestName(false)) {
      override fun getDisplayName(): String = "unique blah-blah" + System.identityHashCode(this)
    }
    val plainTextDialectFileType = MyMockLanguageFileType(language)
    (FileTypeManager.getInstance() as FileTypeManagerImpl).registerFileType(plainTextDialectFileType, listOf(), parentDisposable, descriptor)
    return plainTextDialectFileType.language
  }

  class MyMockLanguageFileType(language: Language) : MockLanguageFileType(language, "x_x_x_x") {
    override fun getDescription(): String = "blah-blah" + System.identityHashCode(this)
  }

  fun `test CompletionExtension extensions are cleared when explicit extension is added`() {
    val unregisterDialectDisposable = Disposer.newDisposable(testRootDisposable, getTestName(false))
    val plainTextDialect = registerLanguageDialect(unregisterDialectDisposable)

    val extensionRegistrationDisposable = registerExtension(myCompletionExtensionPointName, PlainTextLanguage.INSTANCE.id, String::class.java.name)
    assertSize(1, completionExtension.forKey(PlainTextLanguage.INSTANCE))
    assertSize(1, completionExtension.forKey(plainTextDialect))

    Disposer.dispose(unregisterDialectDisposable)
    Disposer.dispose(extensionRegistrationDisposable)
    assertSize(0, completionExtension.forKey(plainTextDialect))
  }
}
