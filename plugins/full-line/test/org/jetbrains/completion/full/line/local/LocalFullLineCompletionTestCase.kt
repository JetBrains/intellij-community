package org.jetbrains.completion.full.line.local

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase
import org.jetbrains.completion.full.line.services.LocalModelsCache
import org.jetbrains.completion.full.line.services.managers.ConfigurableModelsManager
import org.jetbrains.completion.full.line.tasks.SetupLocalModelsTask

abstract class LocalFullLineCompletionTestCase(private val language: Language) : FullLineCompletionTestCase(false) {
  override fun setUp() {
    super.setUp()
    val manager = service<ConfigurableModelsManager>()
    val model = manager.modelsSchema.targetLanguage(language)
    if (model == null) {
      SetupLocalModelsTask(
        myFixture.project,
        SetupLocalModelsTask.ToDoParams(language, SetupLocalModelsTask.Action.DOWNLOAD)
      ).queue()
    }
  }

  protected fun initModel() {
    val model = service<LocalModelsCache>().tryGetModel(language)
    if (model != null) return

    Thread.sleep(5000)
    val modelAfterSleep = service<LocalModelsCache>().tryGetModel(language)
    assertNotNull(modelAfterSleep)

    myFixture.configureByText(fileTypeFromLanguage(language.id), "<caret>")
    myFixture.completeBasic()


    Thread.sleep(5000)
  }
}
