// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.patterns.PsiElementPattern
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.query.*
import com.intellij.psi.PsiElement
import com.intellij.util.SmartList
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

@Service(Service.Level.APP)
class PolySymbolQueryScopeService {

  fun buildScope(project: Project, location: PsiElement?, context: PolyContext, allowResolve: Boolean): List<PolySymbolScope> =
    elementClassToWrappers
      .computeIfAbsent(location?.javaClass ?: Unit::class.java, ::buildWrappersForElementClass)
      .flatMap {
        if (it.filters.accept(context, allowResolve))
          it.getScopes(project, location)
        else
          emptyList()
      }

  @Suppress("TestOnlyProblems")
  private val elementClassToWrappers: MutableMap<Class<*>, List<ScopeProviderWrapper>>
    get() = PolySymbolQueryScopeContributor.EP_NAME.computeIfAbsent(Class::class.java) { ConcurrentHashMap() }

  @Suppress("TestOnlyProblems")
  private val providerWrappers: List<ScopeProviderWrapper>
    get() =
      PolySymbolQueryScopeContributor.EP_NAME.computeIfAbsent(ScopeProviderWrapper::class.java) {
        PolySymbolQueryScopeContributor.EP_NAME.extensionList.flatMap { contributor ->
          buildWrappers(contributor)
        }
      }

  private fun buildWrappersForElementClass(elementClass: Class<*>): List<ScopeProviderWrapper> =
    providerWrappers.filter {
      it.elementClass.let { cls -> cls == null || cls.isAssignableFrom(elementClass) }
    }

  private fun buildWrappers(contributor: PolySymbolQueryScopeContributor): List<ScopeProviderWrapper> =
    PolySymbolQueryScopeProviderRegistrarImpl()
      .also { contributor.registerProviders(it) }
      .scopeProviderWrappers

  private class PolySymbolQueryScopeProviderRegistrarImpl() : PolySymbolQueryScopeProviderRegistrar {
    val scopeProviderWrappers: MutableList<ScopeProviderWrapper> = SmartList()

    override fun <T : PsiElement> forPsiLocation(psiLocationPattern: PsiElementPattern.Capture<T>): PolySymbolQueryScopeProviderRegistrar.Builder<PolySymbolQueryScopeProvider<T>> =
      object : PolySymbolQueryScopeProviderRegistrarBuilderBase<PolySymbolQueryScopeProvider<T>>() {
        override fun contributeScopeProvider(provider: PolySymbolQueryScopeProvider<T>) {
          scopeProviderWrappers.add(LocationScopeProviderWrapper(psiLocationPattern, provider, filters))
        }
      }

    override fun forAnywhere(): PolySymbolQueryScopeProviderRegistrar.Builder<PolySymbolProjectQueryScopeProvider> {
      return object : PolySymbolQueryScopeProviderRegistrarBuilderBase<PolySymbolProjectQueryScopeProvider>() {
        override fun contributeScopeProvider(provider: PolySymbolProjectQueryScopeProvider) {
          scopeProviderWrappers.add(ProjectScopeProviderWrapper(provider, filters))
        }
      }
    }
  }

  private interface ScopeProviderWrapper {
    val elementClass: Class<*>?
    val filters: ScopeProviderWrapperFilters
    fun getScopes(project: Project, location: PsiElement?): List<PolySymbolScope>
  }

  private abstract class PolySymbolQueryScopeProviderRegistrarBuilderBase<T> : PolySymbolQueryScopeProviderRegistrar.Builder<T> {

    private var contextFilter: Predicate<PolyContext>? = null
    private var withResolve: Boolean = true

    protected val filters: ScopeProviderWrapperFilters
      get() = ScopeProviderWrapperFilters(contextFilter, withResolve)

    override fun inContext(contextFilter: Predicate<PolyContext>): PolySymbolQueryScopeProviderRegistrar.Builder<T> = apply {
      this.contextFilter = contextFilter
    }

    override fun withoutResolve(): PolySymbolQueryScopeProviderRegistrar.Builder<T> = apply {
      this.withResolve = false
    }
  }

  private class ScopeProviderWrapperFilters(
    val contextFilter: Predicate<PolyContext>?,
    val withResolve: Boolean,
  ) {
    fun accept(context: PolyContext, allowResolve: Boolean): Boolean =
      contextFilter?.test(context) != false
      && (!withResolve || allowResolve)
  }

  private class LocationScopeProviderWrapper<T : PsiElement?>(
    val pattern: PsiElementPattern.Capture<T>,
    val provider: PolySymbolQueryScopeProvider<T>,
    override val filters: ScopeProviderWrapperFilters,
  ) : ScopeProviderWrapper {

    override val elementClass: Class<T> = pattern.condition.initialCondition.acceptedClass

    override fun getScopes(project: Project, location: PsiElement?): List<PolySymbolScope> {
      if (location == null || !pattern.accepts(location)) return emptyList()

      @Suppress("UNCHECKED_CAST")
      location as T

      return provider.getScopes(location)
        .also { oldScope ->
          // check scope stability
          if (Math.random() < 0.2 && ApplicationManager.getApplication().isInternal) {
            val newScope = provider.getScopes(location)
            if (newScope != oldScope) {
              logger<PolySymbolQueryExecutorFactory>().error(
                "PolySymbolQueryScopeProvider $provider should provide scope, which is the same (by equals()), when called with the same arguments: $oldScope != $newScope")
            }
            if (newScope.hashCode() != oldScope.hashCode()) {
              logger<PolySymbolQueryExecutorFactory>().error(
                "PolySymbolQueryScopeProvider $provider should provide scope, which has the same hashCode(), when called with the same arguments: $oldScope != $newScope")
            }
          }
        }
    }
  }

  private class ProjectScopeProviderWrapper(
    val provider: PolySymbolProjectQueryScopeProvider,
    override val filters: ScopeProviderWrapperFilters,
  ) : ScopeProviderWrapper {

    override val elementClass: Class<*>? = null

    override fun getScopes(project: Project, location: PsiElement?): List<PolySymbolScope> =
      provider.getScopes(project)
        .also { oldScope ->
          // check scope stability
          if (Math.random() < 0.2 && ApplicationManager.getApplication().isInternal) {
            val newScope = provider.getScopes(project)
            if (newScope != oldScope) {
              logger<PolySymbolQueryExecutorFactory>().error(
                "PolySymbolProjectQueryScopeProvider $provider should provide scope, which is the same (by equals()), when called with the same arguments: $oldScope != $newScope")
            }
            if (newScope.hashCode() != oldScope.hashCode()) {
              logger<PolySymbolQueryExecutorFactory>().error(
                "PolySymbolProjectQueryScopeProvider $provider should provide scope, which has the same hashCode(), when called with the same arguments: $oldScope != $newScope")
            }
          }
        }
  }
}