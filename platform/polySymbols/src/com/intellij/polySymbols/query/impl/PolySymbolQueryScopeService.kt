// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.patterns.PsiFilePattern
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.query.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
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
    if (elementClass == Unit::class.java)
      providerWrappers
    else
      providerWrappers.filter {
        it.elementClasses.any { cls -> cls.isAssignableFrom(elementClass) }
      }

  private fun buildWrappers(contributor: PolySymbolQueryScopeContributor): List<ScopeProviderWrapper> =
    PolySymbolQueryScopeProviderRegistrarImpl()
      .also { contributor.registerProviders(it) }
      .scopeProviderWrappers

  private data class PolySymbolQueryScopeProviderRegistrarImpl(
    private val filePatterns: Collection<PsiFilePattern<out PsiFile, *>> = emptyList(),
    private val contextFilter: Predicate<PolyContext>? = null,
    private val withResolve: Boolean = true,
    val scopeProviderWrappers: MutableList<ScopeProviderWrapper> = SmartList(),
  ) : PolySymbolQueryScopeProviderRegistrar {

    override fun inFile(fileClass: Class<out PsiFile>): PolySymbolQueryScopeProviderRegistrarInFileContext =
      copy(filePatterns = listOf(PlatformPatterns.psiFile(fileClass)))

    override fun inFile(filePattern: PsiFilePattern<out PsiFile, *>): PolySymbolQueryScopeProviderRegistrarInFileContext =
      copy(filePatterns = listOf(filePattern))

    override fun inFiles(vararg fileClasses: Class<out PsiFile>): PolySymbolQueryScopeProviderRegistrarInFileContext =
      copy(filePatterns = fileClasses.map { PlatformPatterns.psiFile(it) })

    override fun inFiles(filePatterns: Collection<PsiFilePattern<out PsiFile, *>>): PolySymbolQueryScopeProviderRegistrarInFileContext =
      copy(filePatterns = filePatterns)

    override fun inFiles(vararg filePatterns: PsiFilePattern<out PsiFile, *>): PolySymbolQueryScopeProviderRegistrarInFileContext =
      copy(filePatterns = filePatterns.toList())

    override fun inContext(contextFilter: Predicate<PolyContext>): PolySymbolQueryScopeProviderRegistrar =
      copy(contextFilter = contextFilter)

    override fun withoutResolve(): PolySymbolQueryScopeProviderRegistrar =
      copy(withResolve = false)

    override fun forAnyPsiLocation(): PolySymbolLocationQueryScopeProviderRegistrar<PsiElement> =
      forPsiLocations(listOf(psiElement()))

    override fun <T : PsiElement> forPsiLocation(psiLocationClass: Class<T>): PolySymbolLocationQueryScopeProviderRegistrar<T> =
      forPsiLocations(listOf(psiElement(psiLocationClass)))

    override fun <T : PsiElement> forPsiLocation(psiLocationPattern: PsiElementPattern<T, *>): PolySymbolLocationQueryScopeProviderRegistrar<T> =
      forPsiLocations(listOf(psiLocationPattern))

    override fun <T : PsiElement> forPsiLocations(vararg psiLocationClasses: Class<out T>): PolySymbolLocationQueryScopeProviderRegistrar<T> =
      forPsiLocations(psiLocationClasses.map { psiElement(it) })

    override fun <T : PsiElement> forPsiLocations(vararg psiLocationPatterns: PsiElementPattern<out T, *>): PolySymbolLocationQueryScopeProviderRegistrar<T> =
      forPsiLocations(psiLocationPatterns.toList())

    override fun <T : PsiElement> forPsiLocations(psiLocationPatterns: Collection<PsiElementPattern<out T, *>>): PolySymbolLocationQueryScopeProviderRegistrar<T> =
      PolySymbolQueryScopeProviderBuilder(scopeProviderWrappers, psiLocationPatterns, filePatterns, contextFilter, withResolve)

    override fun forProject(): PolySymbolProjectQueryScopeProviderRegistrar =
      PolySymbolProjectQueryScopeProviderRegistrarImpl(scopeProviderWrappers, contextFilter, withResolve)
  }

  private interface ScopeProviderWrapper {
    val elementClasses: List<Class<*>>
    val filters: ScopeProviderWrapperFilters
    fun getScopes(project: Project, location: PsiElement?): Collection<PolySymbolScope>
  }

  private class PolySymbolQueryScopeProviderBuilder<T : PsiElement>(
    private val scopeProviderWrappers: MutableList<ScopeProviderWrapper>,
    private val psiLocationPatterns: Collection<PsiElementPattern<out T, *>>,
    private var filePatterns: Collection<PsiFilePattern<out PsiFile, *>>,
    private var contextFilter: Predicate<PolyContext>?,
    private var withResolve: Boolean,
  ) : PolySymbolLocationQueryScopeProviderRegistrar<T> {

    override fun inContext(contextFilter: Predicate<PolyContext>): PolySymbolLocationQueryScopeProviderRegistrar<T> = apply {
      this.contextFilter = contextFilter
    }

    override fun withoutResolve(): PolySymbolLocationQueryScopeProviderRegistrar<T> = apply {
      this.withResolve = false
    }

    override fun inFile(filePattern: PsiFilePattern<out PsiFile, *>): PolySymbolLocationQueryScopeProviderRegistrar<T> = apply {
      this.filePatterns = listOf(filePattern)
    }

    override fun inFile(fileClass: Class<out PsiFile>): PolySymbolLocationQueryScopeProviderRegistrar<T> = apply {
      this.filePatterns = listOf(PlatformPatterns.psiFile(fileClass))
    }

    override fun inFiles(vararg filePatterns: PsiFilePattern<out PsiFile, *>): PolySymbolLocationQueryScopeProviderRegistrar<T> = apply {
      this.filePatterns = filePatterns.toList()
    }

    override fun inFiles(vararg fileClasses: Class<out PsiFile>): PolySymbolLocationQueryScopeProviderRegistrar<T> = apply {
      this.filePatterns = fileClasses.map { PlatformPatterns.psiFile(it) }
    }

    override fun inFiles(filePatterns: Collection<PsiFilePattern<out PsiFile, *>>): PolySymbolLocationQueryScopeProviderRegistrar<T> = apply {
      this.filePatterns = filePatterns
    }

    override fun contributeScopeProvider(provider: PolySymbolLocationQueryScopeProvider<T>) {
      scopeProviderWrappers.add(LocationScopeProviderWrapper(psiLocationPatterns, filePatterns, provider, ScopeProviderWrapperFilters(contextFilter, withResolve)))
    }
  }

  private class PolySymbolProjectQueryScopeProviderRegistrarImpl(
    private val scopeProviderWrappers: MutableList<ScopeProviderWrapper>,
    private var contextFilter: Predicate<PolyContext>?,
    private var withResolve: Boolean,
  ) : PolySymbolProjectQueryScopeProviderRegistrar {

    override fun inContext(contextFilter: Predicate<PolyContext>): PolySymbolProjectQueryScopeProviderRegistrar = apply {
      this.contextFilter = contextFilter
    }

    override fun withoutResolve(): PolySymbolProjectQueryScopeProviderRegistrar = apply {
      this.withResolve = false
    }

    override fun contributeScopeProvider(provider: PolySymbolProjectQueryScopeProvider) {
      scopeProviderWrappers.add(ProjectScopeProviderWrapper(provider, ScopeProviderWrapperFilters(contextFilter, withResolve)))
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
    val locationPatterns: Collection<PsiElementPattern<out T, *>>,
    val filePatterns: Collection<PsiFilePattern<out PsiFile, *>>,
    val provider: PolySymbolLocationQueryScopeProvider<T>,
    override val filters: ScopeProviderWrapperFilters,
  ) : ScopeProviderWrapper {

    override val elementClasses: List<Class<*>> = locationPatterns.map { it.condition.initialCondition.acceptedClass }.distinct()

    override fun getScopes(project: Project, location: PsiElement?): Collection<PolySymbolScope> {
      if (location == null) return emptyList()
      val containingFile = location.containingFile
      if (!filePatterns.any { it.accepts(containingFile) }) return emptyList()
      if (!locationPatterns.any { it.accepts(location) }) return emptyList()

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

    override val elementClasses: List<Class<*>> = emptyList()

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