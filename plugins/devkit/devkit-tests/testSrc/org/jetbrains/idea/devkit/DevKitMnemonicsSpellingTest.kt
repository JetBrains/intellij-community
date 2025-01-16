// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.idea.devkit.module.PluginModuleType

private val pluginProjectDescriptor: DefaultLightProjectDescriptor = object : DefaultLightProjectDescriptor() {
  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    addJetBrainsAnnotations(model)
  }

  @Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
  override fun getModuleTypeId(): String = PluginModuleType.ID
}

internal class DevKitMnemonicsSpellingTest : LightJavaCodeInsightFixtureTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = pluginProjectDescriptor

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(SpellCheckingInspection::class.java)
  }

  fun testMnemonics() {
    myFixture.configureByText("MyBundle.properties", """
      action.SwitchCoverage.text=Show Code Co_verage Data
      action.Annotate.text=A_nnotate
      action.UnderscoreTypo.text=<TYPO descr="Typo: In word 'A_nnotatex'">A_nnotatex</TYPO>
      before.check.cleanup.code=C&leanup
      before.AmpersandTypo.code=<TYPO descr="Typo: In word 'C&leanupic'">C&leanupic</TYPO>
    """.trimIndent())
    myFixture.checkHighlighting()
  }
}