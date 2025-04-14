// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.readDescriptorForTest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Assert
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class LanguageExtensionOrderTest : LightPlatformTestCase() {
  private lateinit var myDescriptor: PluginDescriptor
  private lateinit var area: ExtensionsAreaImpl
  private lateinit var myLanguageExtension: LanguageExtension<TestLangExtension>

  override fun setUp() {
    super.setUp()
    myDescriptor = DefaultPluginDescriptor(PluginId.getId(""))
    area = ApplicationManager.getApplication().getExtensionArea() as ExtensionsAreaImpl
    @Suppress("UnresolvedPluginConfigReference")
    myLanguageExtension = LanguageExtension<TestLangExtension>("langExt")
    registerMetaLanguage()
    registerLanguageEP()
  }

  private fun registerMetaLanguage() {
    ApplicationManager.getApplication().extensionArea.getExtensionPoint(MetaLanguage.EP_NAME).registerExtension(MyMetaLanguage.INSTANCE, testRootDisposable)
  }

  private fun registerLanguageEP() {
    area.registerExtensionPoints(myDescriptor, listOf(JDOMUtil.load("""
    <extensionPoint qualifiedName="langExt" beanClass="com.intellij.lang.LanguageExtensionPoint">
      <with attribute="implementationClass" implements="com.intellij.lang.TestLangExtension"/>
    </extensionPoint>    
    """)))

    Disposer.register(testRootDisposable) {
      area.unregisterExtensionPoint("langExt")
    }
  }

  private fun registerExtensions(vararg xmls: String) {
    for (ext in xmls) {
      val moduleXml = "<idea-plugin><extensions>$ext</extensions></idea-plugin>"

      val pluginDescriptor: IdeaPluginDescriptorImpl =
        readDescriptorForTest(Path.of(""), true, moduleXml.toByteArray(StandardCharsets.UTF_8),
                                                     myDescriptor.pluginId)
      pluginDescriptor.registerExtensions(area.nameToPointMap, null)
    }
  }

  private fun doTest(vararg classes: Class<*>) {
    val extensions = myLanguageExtension.allForLanguage(MyTestLanguage.INSTANCE)
    Assert.assertEquals(classes.size, extensions.size)
    val extensionClasses = extensions.map { it::class.java }
    Assert.assertEquals(classes.toList(), extensionClasses)
  }

  fun testLanguageBeforeBaseLanguage() {
    registerExtensions(
      """<extension point="langExt" language="LB" implementationClass="com.intellij.lang.MyBaseExtension"/>""",
      """<extension point="langExt" language="L1" implementationClass="com.intellij.lang.MyTestExtension" order="first"/>"""
    )
    doTest(MyTestExtension::class.java, MyBaseExtension::class.java)
  }

  fun testMetaLanguageBeforeLanguage() {
    registerExtensions(
      """<extension point="langExt" language="L1" implementationClass="com.intellij.lang.MyTestExtension" id="default"/>""",
      """<extension point="langExt" language="M1" implementationClass="com.intellij.lang.MyMetaExtension" order="before default"/>"""
    )
    doTest(MyMetaExtension::class.java, MyTestExtension::class.java)
  }

  fun testMetaLanguageBeforeBaseLanguage() {
    registerExtensions(
      """<extension point="langExt" language="LB" implementationClass="com.intellij.lang.MyBaseExtension"/>""",
      """<extension point="langExt" language="M1" implementationClass="com.intellij.lang.MyMetaExtension" order="last"/>"""
    )
    doTest(MyMetaExtension::class.java, MyBaseExtension::class.java)
  }
}