package org.jetbrains.completion.full.line.providers

import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.TestOnly
import org.jetbrains.completion.full.line.RawFullLineProposal
import org.jetbrains.completion.full.line.models.ModelType
import org.jetbrains.completion.full.line.platform.FullLineCompletionQuery
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import java.util.concurrent.CopyOnWriteArrayList

interface FullLineCompletionProvider {
  fun getVariants(query: FullLineCompletionQuery, indicator: ProgressIndicator): List<RawFullLineProposal>

  fun getId(): String //@NlsSafe String TODO: mark API @NlsSafe when since build is 203+

  companion object {
    private val FOR_TESTS: MutableList<FullLineCompletionProvider> = CopyOnWriteArrayList()

    fun getSuitable(language: Language): List<FullLineCompletionProvider> {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return getTestProviders(language)
      }

      if (Registry.`is`("full.line.use.all.providers")) {
        return ModelType.values().mapNotNull { createProvider(it, language) }
      }

      val modelType = MLServerCompletionSettings.getInstance().getModelMode()
      return listOfNotNull(createProvider(modelType, language))
    }

    @TestOnly
    fun mockProvider(provider: FullLineCompletionProvider, parentDisposable: Disposable) {
      assert(ApplicationManager.getApplication().isUnitTestMode)
      FOR_TESTS.add(provider)
      Disposer.register(parentDisposable) {
        FOR_TESTS.remove(provider)
      }
    }

    private fun createProvider(modelType: ModelType, language: Language): FullLineCompletionProvider? {
      return when (modelType) {
        ModelType.Cloud -> CloudFullLineCompletionProvider()
        ModelType.Local -> LocalFullLineCompletionProvider.create(language)
      }
    }

    private fun getTestProviders(language: Language): List<FullLineCompletionProvider> {
      if (FOR_TESTS.isNotEmpty()) {
        return FOR_TESTS.toList()
      }

      // Try using local provider in tests by default
      return listOfNotNull(createProvider(ModelType.Local, language))
    }
  }
}
