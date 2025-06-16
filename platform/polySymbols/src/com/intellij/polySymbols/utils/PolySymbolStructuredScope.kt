package com.intellij.polySymbols.utils

import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.query.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.SmartList
import com.intellij.util.containers.Stack
import com.intellij.util.takeWhileInclusive

abstract class PolySymbolStructuredScope<T : PsiElement, R : PsiElement>(protected val location: T) : PolySymbolCompoundScope() {

  protected abstract val rootPsiElement: R?

  protected abstract val scopesBuilderProvider: (rootPsiScope: R, holder: PolySymbolPsiScopesHolder) -> PsiElementVisitor?

  protected abstract val providedSymbolKinds: Set<PolySymbolQualifiedKind>

  override fun build(queryExecutor: PolySymbolQueryExecutor, consumer: (PolySymbolScope) -> Unit) {
    getCurrentScope()
      ?.let { consumer(it) }
  }

  protected fun getCurrentScope(): PolySymbolPsiScope? =
    getRootScope()
      ?.let { findBestMatchingScope(it) }
      ?.let {
        val structuredScopePtr = this@PolySymbolStructuredScope.createPointer()
        PolySymbolPsiScopeWithPointer(it) {
          structuredScopePtr.dereference()?.getCurrentScope()
        }
      }

  protected fun getRootScope(): PolySymbolPsiScope? {
    val manager = CachedValuesManager.getManager(location.project)
    val rootPsiElement = rootPsiElement ?: return null
    val scopeBuilderProvider = scopesBuilderProvider
    val providedSymbolKinds = providedSymbolKinds
    return manager
      .getCachedValue(rootPsiElement, manager.getKeyForClass(this.javaClass), {
        val holder = PolySymbolPsiScopesHolder(rootPsiElement, providedSymbolKinds)
        scopeBuilderProvider(rootPsiElement, holder)?.let { rootPsiElement.accept(it) }
        CachedValueProvider.Result.create(holder.topLevelScope, rootPsiElement, PsiModificationTracker.MODIFICATION_COUNT)
      }, false)
  }

  abstract override fun createPointer(): Pointer<out PolySymbolStructuredScope<T, R>>

  override fun equals(other: Any?): Boolean =
    other === this || (
      other is PolySymbolStructuredScope<*, *>
      && other.javaClass === this.javaClass
      && other.location == location)

  override fun hashCode(): Int =
    location.hashCode()

  protected open fun findBestMatchingScope(rootScope: PolySymbolPsiScope): PolySymbolPsiScope? =
    (rootScope as PolySymbolPsiScopeImpl).findBestMatchingScope(location.textOffset)

  protected class PolySymbolPsiScopesHolder(val rootElement: PsiElement, val providedSymbolKinds: Set<PolySymbolQualifiedKind>) {
    private val scopes = Stack<PolySymbolPsiScope>()

    internal val topLevelScope: PolySymbolPsiScope
      get() {
        assert(scopes.size == 1)
        return scopes.peek()
      }

    init {
      scopes.add(PolySymbolPsiScopeImpl(rootElement, emptyMap(), null, providedSymbolKinds, emptySet()))
    }

    fun currentScope(): PolySymbolPsiScope =
      scopes.peek()

    fun previousScope(): PolySymbolPsiScope =
      scopes[scopes.size - 2]

    fun popScope() {
      scopes.pop()
    }

    fun pushScope(
      scopePsiElement: PsiElement,
      properties: Map<String, Any> = emptyMap(),
      exclusiveSymbolKinds: Set<PolySymbolQualifiedKind> = emptySet(),
      updater: (ScopeModifier.() -> Unit)? = null,
    ) {
      val scope = PolySymbolPsiScopeImpl(scopePsiElement, properties,
                                         currentScope() as PolySymbolPsiScopeImpl,
                                         providedSymbolKinds, exclusiveSymbolKinds)
      scopes.push(scope)
      if (updater != null) ScopeModifierImpl(scope).updater()
    }

    fun currentScope(updater: ScopeModifier.() -> Unit) {
      ScopeModifierImpl(currentScope() as PolySymbolPsiScopeImpl).updater()
    }

    fun previousScope(updater: ScopeModifier.() -> Unit) {
      ScopeModifierImpl(previousScope() as PolySymbolPsiScopeImpl).updater()
    }

    interface ScopeModifier {
      fun addSymbol(symbol: PolySymbol)
      fun addSymbols(symbol: List<PolySymbol>)
    }

    private inner class ScopeModifierImpl(private val scope: PolySymbolPsiScopeImpl) : ScopeModifier {
      override fun addSymbol(symbol: PolySymbol) {
        if (symbol.qualifiedKind !in providedSymbolKinds)
          throw IllegalStateException("PolySymbol of kind ${symbol.qualifiedKind} should not be provided by ${this::class.java.name}")
        scope.add(symbol)
      }

      override fun addSymbols(symbol: List<PolySymbol>) {
        symbol.forEach { addSymbol(it) }
      }
    }
  }

  protected interface PolySymbolPsiScope : PolySymbolScope {
    val source: PsiElement
    val parent: PolySymbolPsiScope?
    val properties: Map<String, Any>
    val children: List<PolySymbolPsiScope>
    val localSymbols: List<PolySymbol>
    fun getAllSymbols(qualifiedKind: PolySymbolQualifiedKind): List<PolySymbol>
  }

  private class PolySymbolPsiScopeWithPointer(
    private val delegate: PolySymbolPsiScope,
    private val pointer: Pointer<out PolySymbolPsiScope>,
  ) : PolySymbolPsiScope by delegate {

    override fun createPointer(): Pointer<out PolySymbolPsiScope> =
      pointer

    override fun equals(other: Any?): Boolean =
      other === this || delegate == other ||
      other is PolySymbolPsiScopeWithPointer
      && other.delegate == delegate

    override fun hashCode(): Int =
      delegate.hashCode()
  }

  private class PolySymbolPsiScopeImpl(
    override val source: PsiElement,
    override val properties: Map<String, Any>,
    override val parent: PolySymbolPsiScopeImpl?,
    private val providedSymbolKinds: Set<PolySymbolQualifiedKind>,
    private val exclusiveSymbolKinds: Set<PolySymbolQualifiedKind>,
  ) : PolySymbolPsiScope {

    override val children = ArrayList<PolySymbolPsiScopeImpl>()
    override val localSymbols = SmartList<PolySymbol>()

    private val textRange: TextRange get() = source.textRange
    private val scopesInHierarchy get() = generateSequence(this) { it.parent }

    init {
      @Suppress("LeakingThis")
      parent?.add(this)
    }

    fun findBestMatchingScope(offset: Int): PolySymbolPsiScope? {
      if (!textRange.contains(offset)) {
        return null
      }
      var curScope: PolySymbolPsiScopeImpl? = null
      var innerScope: PolySymbolPsiScopeImpl? = this
      while (innerScope != null) {
        curScope = innerScope
        innerScope = null
        for (child in curScope.children) {
          if (child.textRange.contains(offset)) {
            innerScope = child
            break
          }
        }
      }
      return curScope
    }

    override fun isExclusiveFor(qualifiedKind: PolySymbolQualifiedKind): Boolean =
      scopesInHierarchy.any { it.exclusiveSymbolKinds.contains(qualifiedKind) }

    override fun getSymbols(
      qualifiedKind: PolySymbolQualifiedKind,
      params: PolySymbolListSymbolsQueryParams,
      stack: PolySymbolQueryStack,
    ): List<PolySymbol> =
      getAllSymbols(qualifiedKind)

    override fun getAllSymbols(qualifiedKind: PolySymbolQualifiedKind): List<PolySymbol> =
      if (qualifiedKind in providedSymbolKinds)
      // TODO - consider optimizing in case there are many symbols in the scope
        scopesInHierarchy
          .takeWhileInclusive { !it.isExclusiveFor(qualifiedKind) }
          .flatMap { it.localSymbols }
          .filter { it.qualifiedKind == qualifiedKind }
          .distinctBy { it.name }
          .toList()
      else
        emptyList()

    fun add(symbol: PolySymbol) {
      localSymbols.add(symbol)
    }

    private fun add(scope: PolySymbolPsiScopeImpl) {
      children.add(scope)
    }

    override fun equals(other: Any?): Boolean =
      other is PolySymbolPsiScopeImpl
      && other.source == source
      && other.textRange == textRange

    override fun hashCode(): Int =
      source.hashCode()

    override fun createPointer(): Pointer<out PolySymbolScope> =
      throw IllegalStateException("PolySymbolPsiScopeImpl cannot be pointed to. It should be wrapped with PolySymbolPsiScopeWithPointer.")

    override fun getModificationCount(): Long = 0
  }

}