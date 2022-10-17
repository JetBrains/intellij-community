// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.registry.impl

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.model.Pointer
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
import com.intellij.webSymbols.context.WebSymbolsContextKindRules
import com.intellij.webSymbols.context.WebSymbolsContextRulesProvider
import com.intellij.webSymbols.context.impl.buildWebSymbolsContext
import com.intellij.webSymbols.registry.*
import com.intellij.webSymbols.utils.createModificationTracker
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

    val containers = mutableListOf<WebSymbolsContainer>()
    getCustomContainers(location).forEach(containers::add)
    val internalMode = ApplicationManager.getApplication().isInternal
    val originalLocation = location?.originalElement
    containers.addAll(WebSymbolsRegistryExtension.EP_NAME.extensionList.flatMap { provider ->
      provider.getContainers(project, originalLocation, context, allowResolve)
        .also {
          //check provider
          if (internalMode && Math.random() < 0.2) {
            val newContext = provider.getContainers(project, originalLocation, context, allowResolve)
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

    return WebSymbolsRegistryImpl(containers,
                                  createNamesProvider(project, originalLocation, context),
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

    val providers = mutableListOf<Pointer<out WebSymbolsContextRulesProvider>>()
    for (provider in WebSymbolsRegistryExtension.EP_NAME.extensionList.flatMap {
      it.beforeRegistryCreation(dir.project, dir)
      it.getContextRulesProviders(dir)
    }) {
      result.putAllValues(provider.getContextRules())
      providers.add(provider.createPointer())
    }
    return Pair(result, createModificationTracker(providers))
  }

  private fun createNamesProvider(project: Project, location: PsiElement?, context: WebSymbolsContext): WebSymbolNamesProvider {
    val nameConversionRules = mutableListOf<WebSymbolNameConversionRules>()
    val providers = mutableListOf<Pointer<out WebSymbolNameConversionRulesProvider>>()
    WebSymbolsRegistryExtension.EP_NAME.extensionList.flatMap { provider ->
      provider.getNameConversionRulesProviders(project, location, context)
    }.forEach { provider ->
      nameConversionRules.add(provider.getNameConversionRules())
      providers.add(provider.createPointer())
    }
    return WebSymbolNamesProviderImpl(context.framework, nameConversionRules, createModificationTracker(providers))
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