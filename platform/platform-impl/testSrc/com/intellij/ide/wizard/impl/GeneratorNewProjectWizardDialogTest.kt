// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard.impl

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.LanguageNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.ide.wizard.language.EmptyProjectGeneratorNewProjectWizard
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.Icon

@TestApplication
internal class GeneratorNewProjectWizardDialogTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    resetStoredProjectTypeSelectionForTests()
    @Suppress("DEPRECATION")
    ExtensionTestUtil.maskExtensions(LanguageNewProjectWizard.EP_NAME, emptyList(), disposable)
  }

  @Test
  fun `the wizard without plugins shows only the empty project`(): Unit = timeoutRunBlocking {
    registerLanguages()
    registerGenerators()

    assertEquals("Empty Project", availableGenerators())
  }

  @Test
  fun `the languages are in alphabetical order and the other generators follow them`(): Unit = timeoutRunBlocking {
    registerLanguages(
      TestLanguageGenerator("Rust", ordinal = 1),
      TestLanguageGenerator("Go", ordinal = 3),
      TestLanguageGenerator("Ruby", ordinal = 2),
    )
    registerGenerators(TestGenerator("Workspace", ordinal = 1))

    assertEquals("Go, Ruby, Rust, Workspace, Empty Project", availableGenerators())
  }

  @Test
  fun `a registered empty project generator shows once`(): Unit = timeoutRunBlocking {
    registerLanguages()
    registerGenerators(EmptyProjectGeneratorNewProjectWizard())

    assertEquals("Empty Project", availableGenerators())
  }

  @Test
  fun `a disabled generator is absent`(): Unit = timeoutRunBlocking {
    registerLanguages(TestLanguageGenerator("Go", ordinal = 1, isEnabled = false), TestLanguageGenerator("Rust", ordinal = 2))
    registerGenerators(TestGenerator("Workspace", ordinal = 1, isEnabled = false))

    assertEquals("Rust, Empty Project", availableGenerators())
  }

  @Test
  fun `the wizard selects the generator that the user selected in the previous wizard`(): Unit = timeoutRunBlocking {
    registerLanguages(TestLanguageGenerator("Go", ordinal = 1), TestLanguageGenerator("Rust", ordinal = 2))
    registerGenerators()

    withDialog { dialog -> assertTrue(dialog.setSelectedGenerator("Rust")) }
    withDialog { dialog -> assertEquals("Rust", dialog.selectedGeneratorToString()) }
  }

  private fun registerLanguages(vararg generators: LanguageGeneratorNewProjectWizard) {
    ExtensionTestUtil.maskExtensions(LanguageGeneratorNewProjectWizard.EP_NAME, generators.toList(), disposable)
  }

  private fun registerGenerators(vararg generators: GeneratorNewProjectWizard) {
    ExtensionTestUtil.maskExtensions(GeneratorNewProjectWizard.EP_NAME, generators.toList(), disposable)
  }

  private suspend fun availableGenerators(): String {
    return withDialog { it.availableGeneratorsToString() }
  }

  private suspend fun <T> withDialog(action: (GeneratorNewProjectWizardDialog) -> T): T = withContext(Dispatchers.EDT) {
    val dialog = GeneratorNewProjectWizardDialog(defaultLocation = null)
    try {
      action(dialog)
    }
    finally {
      dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
    }
  }

  private class TestLanguageGenerator(
    override val name: String,
    override val ordinal: Int,
    private val isEnabled: Boolean = true,
  ) : LanguageGeneratorNewProjectWizard {
    override val icon: Icon = EmptyIcon.ICON_16

    override fun isEnabled(context: WizardContext): Boolean = isEnabled

    override fun createStep(parent: NewProjectWizardStep): NewProjectWizardStep = object : AbstractNewProjectWizardStep(parent) {}
  }

  private class TestGenerator(
    override val name: String,
    override val ordinal: Int,
    private val isEnabled: Boolean = true,
  ) : GeneratorNewProjectWizard {
    override val id: String = "test.$name"
    override val icon: Icon = EmptyIcon.ICON_16

    override fun isEnabled(): Boolean = isEnabled

    override fun createStep(context: WizardContext): NewProjectWizardStep = RootNewProjectWizardStep(context)
  }
}
