// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

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
import com.intellij.webSymbols.query.*
import com.intellij.webSymbols.utils.createModificationTracker
import com.intellij.webSymbols.utils.findOriginalFile

class WebSymbolsQueryExecutorFactoryImpl(private val project: Project) : WebSymbolsQueryExecutorFactory, Disposable {

  private val customScope = MultiMap<VirtualFile?, WebSymbolsScope>()
  private var modificationCount = 0L

  override fun create(location: PsiElement?, allowResolve: Boolean): WebSymbolsQueryExecutor {
    val application = ApplicationManager.getApplication()
    application.assertReadAccessAllowed()

    WebSymbolsQueryConfigurator.EP_NAME.extensionList
      .forEach { it.beforeQueryExecutorCreation(project, location) }

    val context = location?.let { buildWebSymbolsContext(it) } ?: WebSymbolsContext.empty()

    val scopeList = mutableListOf<WebSymbolsScope>()
    getCustomScope(location).forEach(scopeList::add)
    val internalMode = ApplicationManager.getApplication().isInternal
    val originalLocation = location?.originalElement
    scopeList.addAll(WebSymbolsQueryConfigurator.EP_NAME.extensionList.flatMap { provider ->
      provider.getScope(project, originalLocation, context, allowResolve)
        .also {
          //check provider
          if (internalMode && Math.random() < 0.2) {
            val newContext = provider.getScope(project, originalLocation, context, allowResolve)
            if (newContext != it) {
              logger<WebSymbolsQueryExecutorFactory>().error(
                "Provider $provider should provide additional context, which is the same (by equals()), when called with the same arguments: $it != $newContext")
            }
            if (newContext.hashCode() != it.hashCode()) {
              logger<WebSymbolsQueryExecutorFactory>().error(
                "Provider $provider should provide additional context, which has the same hashCode(), when called with the same arguments: $it != $newContext")
            }
          }
        }
    })

    return WebSymbolsQueryExecutorImpl(scopeList,
                                       createNamesProvider(project, originalLocation, context),
                                       WebSymbolsQueryResultsCustomizerFactory.getScope(location, context),
                                       context,
                                       allowResolve)
  }

  override fun addScope(scope: WebSymbolsScope,
                        contextDirectory: VirtualFile?,
                        disposable: Disposable) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    modificationCount++
    customScope.putValue(contextDirectory, scope)
    Disposer.register(disposable) {
      customScope.remove(contextDirectory, scope)
    }
  }

  override fun dispose() {
  }

  internal fun getContextRules(dir: PsiDirectory): Pair<MultiMap<ContextKind, WebSymbolsContextKindRules>, ModificationTracker> {
    val result = MultiMap<ContextKind, WebSymbolsContextKindRules>()

    getCustomScope(dir)
      .filterIsInstance<WebSymbolsContextRulesProvider>()
      .map { it.getContextRules() }
      .forEach {
        result.putAllValues(it)
      }

    val providers = mutableListOf<Pointer<out WebSymbolsContextRulesProvider>>()
    for (provider in WebSymbolsQueryConfigurator.EP_NAME.extensionList.flatMap {
      it.beforeQueryExecutorCreation(dir.project, dir)
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
    WebSymbolsQueryConfigurator.EP_NAME.extensionList.flatMap { provider ->
      provider.getNameConversionRulesProviders(project, location, context)
    }.forEach { provider ->
      nameConversionRules.add(provider.getNameConversionRules())
      providers.add(provider.createPointer())
    }
    return WebSymbolNamesProviderImpl(context.framework, nameConversionRules, createModificationTracker(providers))
  }

  private fun getCustomScope(context: PsiElement?): List<WebSymbolsScope> {
    val file = context
      ?.let { InjectedLanguageManager.getInstance(it.project).getTopLevelFile(it) }
      ?.originalFile
      ?.virtualFile
      ?.let { findOriginalFile(it) }
      ?.let { return emptyList() }

    val result = mutableListOf<WebSymbolsScope>()
    if (!customScope.isEmpty) {
      result.addAll(customScope.get(null))
      var f: VirtualFile? = file
      @Suppress("KotlinConstantConditions")
      while (f != null) {
        if (customScope.containsKey(f)) {
          result.addAll(customScope.get(f))
        }
        f = f.parent
      }
    }
    return result
  }

}