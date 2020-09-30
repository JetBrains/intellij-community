// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.ranker

import com.intellij.internal.ml.completion.RankingModelProvider
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.streams.toList

@ApiStatus.Internal
interface ExperimentModelProvider : RankingModelProvider {
  fun experimentGroupNumber(): Int

  companion object {
    private val EP_NAME: ExtensionPointName<RankingModelProvider> = ExtensionPointName("com.intellij.completion.ml.model")

    @JvmStatic
    fun findProvider(language: Language, groupNumber: Int): RankingModelProvider? {
      val (experimentProviders, defaultProviders) = availableProviders()
        .filter { it.match(language, groupNumber) }
        .partition { it is ExperimentModelProvider }

      check(defaultProviders.size <= 1) { "Too many default providers: $defaultProviders" }

      val defaultProvider = defaultProviders.singleOrNull()
      if (experimentProviders.isEmpty()) return defaultProvider

      check(experimentProviders.size == 1) { "Too many experiment provider matching language ${language.displayName} and group number $groupNumber: $experimentProviders" }
      return experimentProviders.singleOrNull() ?: defaultProvider
    }

    fun RankingModelProvider.match(language: Language, groupNumber: Int): Boolean =
      isLanguageSupported(language) && (this !is ExperimentModelProvider || experimentGroupNumber() == groupNumber)

    fun availableProviders(): List<RankingModelProvider> = EP_NAME.extensions().toList()

    @JvmStatic
    fun enabledByDefault(): List<String> {
      return availableProviders().filter { it.isEnabledByDefault }.map { it.id }.toList()
    }

    @TestOnly
    fun registerProvider(provider: RankingModelProvider, parentDisposable: Disposable) {
      val extensionPoint = Extensions.getRootArea().getExtensionPoint(EP_NAME)
      extensionPoint.registerExtension(provider, parentDisposable)
    }
  }
}
