// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.polySymbols.PolyContextKind
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.context.PolyContextKindRules
import com.intellij.polySymbols.context.PolyContextRulesProvider
import com.intellij.polySymbols.context.impl.buildPolyContext
import com.intellij.polySymbols.query.PolySymbolNameConversionRules
import com.intellij.polySymbols.query.PolySymbolNameConversionRulesProvider
import com.intellij.polySymbols.query.PolySymbolNamesProvider
import com.intellij.polySymbols.query.PolySymbolQueryConfigurator
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.query.PolySymbolQueryResultsCustomizerFactory
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.utils.PolySymbolPrioritizedScope
import com.intellij.polySymbols.utils.createModificationTracker
import com.intellij.polySymbols.utils.findOriginalFile
import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.MultiMap

class PolySymbolQueryExecutorFactoryImpl(private val project: Project) : PolySymbolQueryExecutorFactory, Disposable {

  private val customScope = MultiMap<VirtualFile?, PolySymbolScope>()
  private var modificationCount = 0L

  override fun create(location: PsiElement?, allowResolve: Boolean): PolySymbolQueryExecutor {
    ThreadingAssertions.assertReadAccess()

    PolySymbolQueryConfigurator.EP_NAME.extensionList
      .forEach { it.beforeQueryExecutorCreation(project) }

    val context = location?.let { buildPolyContext(it) } ?: PolyContext.empty()

    val scopeOrigin = mutableMapOf<PolySymbolScope, Any>()
    val scopeList = mutableListOf<PolySymbolScope>()
    getCustomScope(location).forEach(scopeList::add)
    val originalLocation = location?.originalElement
    service<PolySymbolQueryScopeService>()
      .buildScope(project, originalLocation, context, allowResolve)
      .forEach { (scope, origin) ->
        scopeList.add(scope)
        scopeOrigin[scope] = origin
      }

    scopeList.sortBy { (it.asSafely<PolySymbolPrioritizedScope>()?.priority ?: PolySymbol.Priority.NORMAL).value }

    return PolySymbolQueryExecutorImpl(location,
                                       scopeList,
                                       scopeOrigin,
                                       createNamesProvider(project, originalLocation, context),
                                       PolySymbolQueryResultsCustomizerFactory.getQueryResultsCustomizer(location, context),
                                       context,
                                       allowResolve)
  }

  override fun addScope(
    scope: PolySymbolScope,
    contextDirectory: VirtualFile?,
    disposable: Disposable,
  ) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    modificationCount++
    customScope.putValue(contextDirectory, scope)
    Disposer.register(disposable) {
      customScope.remove(contextDirectory, scope)
    }
  }

  override fun dispose() {
  }

  internal fun getContextRules(
    project: Project,
    dir: VirtualFile,
  ): Pair<MultiMap<PolyContextKind, PolyContextKindRules>, ModificationTracker> {
    val result = MultiMap<PolyContextKind, PolyContextKindRules>()

    getCustomScope(dir)
      .filterIsInstance<PolyContextRulesProvider>()
      .map { it.getContextRules() }
      .forEach {
        result.putAllValues(it)
      }

    val providers = mutableListOf<Pointer<out ModificationTracker>>()
    for (provider in PolySymbolQueryConfigurator.EP_NAME.extensionList.flatMap {
      it.beforeQueryExecutorCreation(project)
      it.getContextRulesProviders(project, dir)
    }) {
      result.putAllValues(provider.getContextRules())
      val providerPtr = provider.createPointer()
      providers.add(Pointer { providerPtr.dereference()?.modificationTracker })
    }
    return Pair(result, createModificationTracker(providers))
  }

  private fun createNamesProvider(project: Project, location: PsiElement?, context: PolyContext): PolySymbolNamesProvider {
    val nameConversionRules = mutableListOf<PolySymbolNameConversionRules>()
    val providers = mutableListOf<Pointer<out PolySymbolNameConversionRulesProvider>>()
    PolySymbolQueryConfigurator.EP_NAME.extensionList.flatMap { provider ->
      provider.getNameConversionRulesProviders(project, location, context)
    }.forEach { provider ->
      nameConversionRules.add(provider.getNameConversionRules())
      providers.add(provider.createPointer())
    }
    val trackers = providers.map { providerPointer ->
      Pointer { providerPointer.dereference()?.modificationTracker }
    }
    return PolySymbolNamesProviderImpl(context, nameConversionRules, createModificationTracker(trackers))
  }

  private fun getCustomScope(context: PsiElement?): List<PolySymbolScope> =
    context
      ?.let { InjectedLanguageManager.getInstance(it.project).getTopLevelFile(it) }
      ?.originalFile
      ?.virtualFile
      ?.let { findOriginalFile(it) }
      ?.let { getCustomScope(it) }
    ?: emptyList()

  private fun getCustomScope(context: VirtualFile): List<PolySymbolScope> {
    val result = mutableListOf<PolySymbolScope>()
    if (!customScope.isEmpty) {
      result.addAll(customScope.get(null))
      var f: VirtualFile? = context
      while (f != null) {
        if (customScope.containsKey(f)) {
          result.addAll(customScope.get(f))
        }
        f = f.parent
      }
    }
    return result
  }

  private object ScopesPriorityComparator : Comparator<PolySymbolScope> {
    override fun compare(a: PolySymbolScope, b: PolySymbolScope): Int {
      if (a == b) return 0
      val priorityA = ((a as? PolySymbolPrioritizedScope)?.priority ?: PolySymbol.Priority.NORMAL).value
      val priorityB = ((b as? PolySymbolPrioritizedScope)?.priority ?: PolySymbol.Priority.NORMAL).value
      if (priorityA != priorityB) return priorityA.compareTo(priorityB)
      return System.identityHashCode(a).compareTo(System.identityHashCode(b))
    }
  }

}