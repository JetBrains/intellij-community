// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit

import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.module.PluginModuleType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val pluginProjectDescriptor: DefaultLightProjectDescriptor = object : DefaultLightProjectDescriptor() {
  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    addJetBrainsAnnotations(model)
  }

  @Suppress("MISSING_DEPENDENCY_SUPERCLASS_IN_TYPE_ARGUMENT")
  override fun getModuleTypeId(): String = PluginModuleType.ID
}

@TestApplication
internal class DevKitMnemonicsSpellingTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(pluginProjectDescriptor)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Throws(Exception::class)
  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(GrazieSpellCheckingInspection::class.java)
  }

  @Test
  fun testMnemonics() {
    myFixture.configureByText("MyBundle.properties", """
      action.SwitchCoverage.text=Show Code Co_verage Data
      action.Annotate.text=A_nnotate
      action.UnderscoreTypo.text=<TYPO descr="Typo: In word 'Annotatex'">A_nnotatex</TYPO>
      before.check.cleanup.code=C&leanup
      before.AmpersandTypo.code=<TYPO descr="Typo: In word 'Cleanupic'">C&leanupic</TYPO>
    """.trimIndent())
    myFixture.checkHighlighting()
  }
}