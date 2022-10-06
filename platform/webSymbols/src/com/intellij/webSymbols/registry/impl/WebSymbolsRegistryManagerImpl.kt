// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.registry.impl

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import com.intellij.webSymbols.*
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.KIND_FRAMEWORK
import com.intellij.webSymbols.context.WebSymbolsContextKindRules
import com.intellij.webSymbols.context.WebSymbolsContextRulesProvider
import com.intellij.webSymbols.context.impl.buildWebSymbolsContext
import com.intellij.webSymbols.registry.*
import com.intellij.webSymbols.utils.findOriginalFile

class WebSymbolsRegistryManagerImpl(private val project: Project) : WebSymbolsRegistryManager, Disposable {

  private val customContainers = MultiMap<VirtualFile?, WebSymbolsContainer>()
  private var modificationCount = 0L

  override fun get(location: PsiElement?, allowResolve: Boolean): WebSymbolsRegistry {
    val application = ApplicationManager.getApplication()
    application.assertReadAccessAllowed()

    WebSymbolsRegistryExtension.EP_NAME.extensionList
      .forEach { it.beforeRegistryCreation(project, location) }

    val context = location?.let { buildWebSymbolsContext(it) } ?: WebSymbolsContext.empty()

    val result = mutableListOf<WebSymbolsContainer>()
    getCustomContainers(location).forEach(result::add)
    val internalMode = ApplicationManager.getApplication().isInternal
    val originalContextElement = location?.originalElement
    result.addAll(WebSymbolsRegistryExtension.EP_NAME.extensionList.flatMap { provider ->
      provider.getContainers(project, originalContextElement, context, allowResolve)
        .also {
          //check provider
          if (internalMode && Math.random() < 0.2) {
            val newContext = provider.getContainers(project, originalContextElement, context, allowResolve)
            if (newContext != it) {
              logger<WebSymbolsRegistryManager>().error(
                "Provider $provider should provide additional context, which is the same (by equals()), when called with the same arguments: $it != $newContext")
            }
            if (newContext.hashCode() != it.hashCode()) {
              logger<WebSymbolsRegistryManager>().error(
                "Provider $provider should provide additional context, which has the same hashCode(), when called with the same arguments: $it != $newContext")
            }
          }
        }
    })

    return WebSymbolsRegistryImpl(result,
                                  // TODO add separate API for WebSymbolNameConversionRules
                                  WebSymbolNamesProviderImpl(context[KIND_FRAMEWORK], result.filterIsInstance<WebSymbolNameConversionRules>()),
                                  WebSymbolsScopeProvider.getScope(location, context),
                                  context,
                                  allowResolve)
  }

  override fun addSymbolsContainer(container: WebSymbolsContainer,
                                   contextDirectory: VirtualFile?,
                                   disposable: Disposable) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    modificationCount++
    customContainers.putValue(contextDirectory, container)
    Disposer.register(disposable) {
      customContainers.remove(contextDirectory, container)
    }
  }

  override fun dispose() {
  }

  internal fun getContextRules(dir: PsiDirectory): Pair<MultiMap<ContextKind, WebSymbolsContextKindRules>, ModificationTracker> {
    val result = MultiMap<ContextKind, WebSymbolsContextKindRules>()

    getCustomContainers(dir)
      .filterIsInstance<WebSymbolsContextRulesProvider>()
      .map { it.getContextRules() }
      .forEach {
        result.putAllValues(it)
      }

    val trackers = mutableListOf<ModificationTracker>()
    for ((configs, tracker) in WebSymbolsRegistryExtension.EP_NAME.extensionList.map { it.getContextRules(dir) }) {
      result.putAllValues(configs)
      if (tracker != ModificationTracker.NEVER_CHANGED) {
        trackers.add(tracker)
      }
    }
    return Pair(result, ModificationTracker {
      var modCount = 0L
      for (tracker in trackers) {
        modCount += tracker.modificationCount.also { if (it < 0) return@ModificationTracker -1 }
      }
      modCount
    })
  }

  private fun getCustomContainers(context: PsiElement?): List<WebSymbolsContainer> {
    val file = context
      ?.let { InjectedLanguageManager.getInstance(it.project).getTopLevelFile(it) }
      ?.originalFile
      ?.virtualFile
      ?.let { findOriginalFile(it) }
      ?.let { return emptyList() }

    val result = mutableListOf<WebSymbolsContainer>()
    if (!customContainers.isEmpty) {
      result.addAll(customContainers.get(null))
      var f: VirtualFile? = file
      @Suppress("KotlinConstantConditions")
      while (f != null) {
        if (customContainers.containsKey(f)) {
          result.addAll(customContainers.get(f))
        }
        f = f.parent
      }
    }
    return result
  }

}