// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.codeInsight

import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.diagnostic.ITNReporter
import com.intellij.notification.impl.NotificationGroupEP
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.ui.components.JBList
import com.intellij.util.IncorrectOperationException
import com.intellij.util.PathUtil
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspection
import org.jetbrains.idea.devkit.inspections.UnresolvedPluginConfigReferenceInspection
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import java.nio.file.Paths

class KotlinFirPluginXmlFunctionalTest : JavaCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

  override fun setUp() {
    setUpWithKotlinPlugin {
      super.setUp()
      myFixture.enableInspections(PluginXmlDomInspection::class.java)
    }
  }

  override fun getBasePath(): @NonNls String? {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/devkit-kotlin-fir-tests/testData/codeInsight"
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    //include kotlin in the same library as java annotations
    //otherwise annotation targets are not converted, see `buildEnumCall` at `org/jetbrains/kotlin/fir/java/javaAnnotationsMapping.kt:144`
    //because kotlin builtins are not found in library session
    moduleBuilder.addLibrary("annotations", TestKotlinArtifacts.kotlinStdlib.canonicalPath, PathUtil.getJarPathForClass(XCollection::class.java))
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath())
    moduleBuilder.addLibrary("platform-core", PathUtil.getJarPathForClass(RegistryManager::class.java))
    moduleBuilder.addLibrary("platform-ide", PathUtil.getJarPathForClass(JBList::class.java))
    moduleBuilder.addLibrary("platform-ide-impl", PathUtil.getJarPathForClass(ITNReporter::class.java))
    moduleBuilder.addLibrary("platform-util-base", PathUtil.getJarPathForClass(IncorrectOperationException::class.java))
    //moduleBuilder.addLibrary("platform-util", PathUtil.getJarPathForClass(Iconable::class.java))
    moduleBuilder.addLibrary("platform-analysis", PathUtil.getJarPathForClass(LocalInspectionEP::class.java))
    moduleBuilder.addLibrary("platform-resources", Paths.get(PathUtil.getJarPathForClass(LocalInspectionEP::class.java))
      .resolveSibling("intellij.platform.resources").toString())
    moduleBuilder.addLibrary("platform-ide-core", PathUtil.getJarPathForClass(Configurable::class.java))
    moduleBuilder.addLibrary("platform-ide-core-impl", PathUtil.getJarPathForClass(NotificationGroupEP::class.java))
    moduleBuilder.addLibrary("platform-editor", PathUtil.getJarPathForClass(AdvancedSettings::class.java))
    moduleBuilder.addLibrary("platform-extensions", PathUtil.getJarPathForClass(BaseExtensionPointName::class.java))
  }

  fun testCustomAttribute() {
    myFixture.testHighlightingAllFiles(true, false, false, "MyBean.kt", "plugin.xml")
  }

  fun testRegistryKeyIdHighlighting() {
    myFixture.addFileToProject("Registry.kt", """
package com.intellij.openapi.util.registry

class Registry {
  companion object {
    @JvmStatic fun get(key: String): RegistryValue
    @JvmStatic fun `is`(key: String): Boolean
    @JvmStatic fun `is`(key: String, defaultValue: Boolean): Boolean
    @JvmStatic fun intValue(key: String): Int
    @JvmStatic fun intValue(key: String, defaultValue: Int): Int
    @JvmStatic fun doubleValue(key: String, defaultValue: Double): Double
    @JvmStatic fun doubleValue(key: String): Double
    @JvmStatic fun stringValue(key: String): String
    @JvmStatic fun intValue(key: String, defaultValue: Int, minValue: Int, maxValue: Int): Int
  }
}
""")
    myFixture.enableInspections(UnresolvedPluginConfigReferenceInspection())

    myFixture.testHighlighting(true, false, false, "RegistryKeyId.kt", "registryKeyId.xml")
  }
}

