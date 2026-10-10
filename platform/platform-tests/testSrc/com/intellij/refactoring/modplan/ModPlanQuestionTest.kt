// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.codeInspection.options.OptCheckbox
import com.intellij.codeInspection.options.OptControl
import com.intellij.codeInspection.options.OptCustom
import com.intellij.codeInspection.options.OptDropdown
import com.intellij.codeInspection.options.OptGroup
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptionContainer
import com.intellij.codeInspection.ui.OptPaneUtils
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.modcommand.ModEditOptions
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ModPlanQuestionTest : BasePlatformTestCase() {
  private enum class Mode { FIRST, SECOND }

  private val flag = ModPlanOption.bool("flag", false) { "Flag" }
  private val mode = ModPlanOption.choice("mode", Mode::class.java, Mode.FIRST, { "Mode" }) { it.name.lowercase() }
  private val nested = ModPlanOption.bool("nested", true) { "Nested" }
  private val receivedValues = ArrayList<ModPlanOptionValues>()

  /** A group with a checkbox that enables [nested], and the dropdown of [mode]. */
  private fun form(storage: ModPlanQuestion.Storage? = null): ModPlanQuestion.Form = ModPlanQuestion.Form(
    OptPane.pane(OptPane.group("Group", OptPane.checkbox("flag", "Flag", OptPane.checkbox("nested", "Nested")), OptPane.dropdown("mode", "Mode", Mode::class.java) { it.name })),
    listOf(flag, nested, mode),
    storage,
  )

  private inner class StubQuestion : ModPlanQuestion.Custom {
    override fun options(): List<ModPlanOption<*>> = listOf(flag)

    override fun ask(project: Project, currentValues: ModPlanOptionValues): ModPlanOptionValues? {
      fail("A custom question must not be asked yet")
      return null
    }
  }

  private fun ask(question: ModPlanQuestion, initialValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY): ModEditOptions<*> {
    return ModPlanQuestion.askCommand("Title", question, initialValues) { answers ->
      receivedValues.add(answers)
      ModCommand.nop()
    } as ModEditOptions<*>
  }

  /** Answers with [answers], as its UI would; null stands for the cancel of the UI. */
  private inner class AnsweringQuestion(private val answers: ModPlanOptionValues?) : ModPlanQuestion.Custom {
    override fun options(): List<ModPlanOption<*>> = listOf(flag)

    override fun ask(project: Project, currentValues: ModPlanOptionValues): ModPlanOptionValues? = answers
  }

  private class NoOptionsQuestion : ModPlanQuestion.Custom {
    override fun options(): List<ModPlanOption<*>> = emptyList()

    override fun ask(project: Project, currentValues: ModPlanOptionValues): ModPlanOptionValues = currentValues
  }

  fun `test a form of one option is answered by an explicit value of its option`() {
    val answers = ModPlanOptionValues.EMPTY.with(flag, false)

    assertTrue(ModPlanQuestion.Form.of(listOf(flag), null).isAnswered(answers))
    assertFalse(ModPlanQuestion.Form.of(listOf(mode), null).isAnswered(answers))
  }

  fun `test a custom question is answered when all its optionValues have explicit values`() {
    val customQuestion = StubQuestion()
    val noOptionsQuestion = NoOptionsQuestion()
    val answers = ModPlanOptionValues.EMPTY.with(flag, true)

    assertFalse(customQuestion.isAnswered(ModPlanOptionValues.EMPTY))
    assertTrue(customQuestion.isAnswered(answers))
    assertFalse(noOptionsQuestion.isAnswered(answers))
  }

  fun `test batch execution of the command uses the defaults`() {
    val file = myFixture.addFileToProject("a.txt", "a")

    ModCommandExecutor.getInstance().executeInBatch(ActionContext.from(null, file), ask(ModPlanQuestion.Form.of(listOf(flag, mode), null)))

    assertFalse(receivedValues.single().get(flag))
    assertEquals(Mode.FIRST, receivedValues.single().get(mode))
  }

  fun `test answers become optionValues`() {
    ask(ModPlanQuestion.Form.of(listOf(flag, mode), null)).applyOptions(mapOf("flag" to true, "mode" to "SECOND"))

    assertTrue(receivedValues.single().get(flag))
    assertEquals(Mode.SECOND, receivedValues.single().get(mode))
  }

  fun `test choice option is a dropdown and boolean a checkbox`() {
    val checkbox = (ask(ModPlanQuestion.Form.of(listOf(flag), null)).containerSupplier().get() as OptionContainer)
      .optionsPane.components().single() as OptCheckbox
    val dropdown = (ask(ModPlanQuestion.Form.of(listOf(mode), null)).containerSupplier().get() as OptionContainer)
      .optionsPane.components().single() as OptDropdown

    assertEquals("flag", checkbox.bindId())
    assertEquals("mode", dropdown.bindId())
    assertEquals(listOf("FIRST", "SECOND"), dropdown.options().map { it.key() })
  }

  fun `test a custom question is a dialog with the UI of the question`() {
    val customQuestion = StubQuestion()
    val customCommand = ask(customQuestion, ModPlanOptionValues.EMPTY.with(mode, Mode.SECOND))

    val customContainer = customCommand.containerSupplier().get() as OptionContainer
    val customAnswers = customContainer.optionController.getOption(ModPlanQuestion.Custom.ANSWERS_BIND_ID) as ModPlanOptionValues
    customCommand.applyOptions(mapOf(ModPlanQuestion.Custom.ANSWERS_BIND_ID to customAnswers.with(flag, true)))

    assertEquals(ModPlanQuestion.Custom.COMPONENT_ID, (customContainer.optionsPane.components().single() as OptCustom).componentId())
    assertSame(customQuestion, customContainer.optionController.getOption(ModPlanQuestion.Custom.QUESTION_BIND_ID))
    assertEquals(Mode.SECOND, receivedValues.single().get(mode))
    assertTrue(receivedValues.single().get(flag))
  }

  fun `test batch execution of a custom question keeps the initial values`() {
    val file = myFixture.addFileToProject("a.txt", "a")

    ModCommandExecutor.getInstance().executeInBatch(ActionContext.from(null, file), ask(StubQuestion(), ModPlanOptionValues.EMPTY.with(flag, true)))

    assertTrue(receivedValues.single().get(flag))
  }

  fun `test the optionValues dialog of a custom question shows the UI of the question instead`() {
    val answers = ModPlanOptionValues.EMPTY.with(flag, true)
    val container = ask(AnsweringQuestion(answers)).containerSupplier().get() as OptionContainer
    var accepted = false

    OptPaneUtils.editOptions(project, container, "Title") { accepted = true }

    assertTrue(accepted)
    assertEquals(answers, container.optionController.getOption(ModPlanQuestion.Custom.ANSWERS_BIND_ID))
  }

  fun `test the cancel of the UI of a custom question cancels the step`() {
    var accepted = false

    OptPaneUtils.editOptions(project, ask(AnsweringQuestion(null)).containerSupplier().get() as OptionContainer, "Title") { accepted = true }

    assertFalse(accepted)
  }

  fun `test several optionValues make one form with their controls in order`() {
    val formQuestion = ModPlanQuestion.Form.of(listOf(flag, mode), null)

    val components = (ask(formQuestion).containerSupplier().get() as OptionContainer).optionsPane.components()

    assertEquals(listOf("flag", "mode"), components.map { (it as OptControl).bindId() })
    assertFalse(formQuestion.isAnswered(ModPlanOptionValues.EMPTY.with(flag, true)))
  }

  fun `test a form shows its pane as it is`() {
    val layoutForm = form()

    val pane = (ask(layoutForm).containerSupplier().get() as OptionContainer).optionsPane

    assertSame(layoutForm.pane(), pane)
    val group = pane.components().single() as OptGroup
    val checkbox = group.children()[0] as OptCheckbox
    assertEquals("flag", checkbox.bindId())
    assertEquals("nested", (checkbox.children().single() as OptCheckbox).bindId())
    assertEquals("mode", (group.children()[1] as OptDropdown).bindId())
  }

  fun `test the answers of a form become optionValues`() {
    ask(form()).applyOptions(mapOf("flag" to true, "nested" to false, "mode" to "SECOND"))

    assertTrue(receivedValues.single().get(flag))
    assertFalse(receivedValues.single().get(nested))
    assertEquals(Mode.SECOND, receivedValues.single().get(mode))
  }

  fun `test batch execution of a form takes the defaults`() {
    val file = myFixture.addFileToProject("a.txt", "a")

    ModCommandExecutor.getInstance().executeInBatch(ActionContext.from(null, file), ask(form()))

    assertFalse(receivedValues.single().get(flag))
    assertTrue(receivedValues.single().get(nested))
    assertEquals(Mode.FIRST, receivedValues.single().get(mode))
  }

  fun `test a form is answered when all its optionValues have explicit values`() {
    val formQuestion = form()
    val partialValues = ModPlanOptionValues.EMPTY.with(flag, true).with(nested, false)

    assertFalse(formQuestion.isAnswered(partialValues))
    assertTrue(formQuestion.isAnswered(partialValues.with(mode, Mode.FIRST)))
  }

  fun `test a form needs one control for each of its optionValues`() {
    assertThrows(IllegalArgumentException::class.java) {
      ModPlanQuestion.Form(OptPane.pane(OptPane.checkbox("flag", "Flag")), listOf(flag, mode), null)
    }
    assertThrows(IllegalArgumentException::class.java) {
      ModPlanQuestion.Form(OptPane.pane(OptPane.checkbox("nested", "Nested")), listOf(flag), null)
    }
  }

  fun `test a form gives its storage and a custom question none`() {
    val storage = object : ModPlanQuestion.Storage {
      override fun load(optionValues: ModPlanOptionValues): ModPlanOptionValues = optionValues

      override fun store(answers: ModPlanOptionValues) {}
    }

    assertSame(storage, form(storage).storage())
    assertNull(StubQuestion().storage())
  }
}
