package com.intellij.polySymbols.utils

import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.SmartList
import com.intellij.util.containers.Stack
import com.intellij.util.takeWhileInclusive
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.query.PolySymbolsCompoundScope
import com.intellij.polySymbols.query.WebSymbolsListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolsQueryExecutor

abstract class PolySymbolsStructuredScope<T : PsiElement, R : PsiElement>(protected val location: T) : PolySymbolsCompoundScope() {

  protected abstract val rootPsiElement: R?

  protected abstract val scopesBuilderProvider: (rootPsiScope: R, holder: WebSymbolsPsiScopesHolder) -> PsiElementVisitor?

  protected abstract val providedSymbolKinds: Set<PolySymbolQualifiedKind>

  override fun build(queryExecutor: PolySymbolsQueryExecutor, consumer: (PolySymbolsScope) -> Unit) {
    getCurrentScope()
      ?.let { consumer(it) }
  }

  protected fun getCurrentScope(): PolySymbolsPsiScope? =
    getRootScope()
      ?.let { findBestMatchingScope(it) }
      ?.let {
        val structuredScopePtr = this@PolySymbolsStructuredScope.createPointer()
        WebSymbolsPsiScopeWithPointer(it) {
          structuredScopePtr.dereference()?.getCurrentScope()
        }
      }

  protected fun getRootScope(): PolySymbolsPsiScope? {
    val manager = CachedValuesManager.getManager(location.project)
    val rootPsiElement = rootPsiElement ?: return null
    val scopeBuilderProvider = scopesBuilderProvider
    val providedSymbolKinds = providedSymbolKinds
    return manager
      .getCachedValue(rootPsiElement, manager.getKeyForClass(this.javaClass), {
        val holder = WebSymbolsPsiScopesHolder(rootPsiElement, providedSymbolKinds)
        scopeBuilderProvider(rootPsiElement, holder)?.let { rootPsiElement.accept(it) }
        CachedValueProvider.Result.create(holder.topLevelScope, rootPsiElement, PsiModificationTracker.MODIFICATION_COUNT)
      }, false)
  }

  abstract override fun createPointer(): Pointer<out PolySymbolsStructuredScope<T, R>>

  override fun equals(other: Any?): Boolean =
    other === this || (
      other is PolySymbolsStructuredScope<*, *>
      && other.javaClass === this.javaClass
      && other.location == location)

  override fun hashCode(): Int =
    location.hashCode()

  protected open fun findBestMatchingScope(rootScope: PolySymbolsPsiScope): PolySymbolsPsiScope? =
    (rootScope as PolySymbolsPsiScopeImpl).findBestMatchingScope(location.textOffset)

  protected class WebSymbolsPsiScopesHolder(val rootElement: PsiElement, val providedSymbolKinds: Set<PolySymbolQualifiedKind>) {
    private val scopes = Stack<PolySymbolsPsiScope>()

    internal val topLevelScope: PolySymbolsPsiScope
      get() {
        assert(scopes.size == 1)
        return scopes.peek()
      }

    init {
      scopes.add(PolySymbolsPsiScopeImpl(rootElement, emptyMap(), null, providedSymbolKinds, emptySet()))
    }

    fun currentScope(): PolySymbolsPsiScope =
      scopes.peek()

    fun previousScope(): PolySymbolsPsiScope =
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
      val scope = PolySymbolsPsiScopeImpl(scopePsiElement, properties,
                                          currentScope() as PolySymbolsPsiScopeImpl,
                                          providedSymbolKinds, exclusiveSymbolKinds)
      scopes.push(scope)
      if (updater != null) ScopeModifierImpl(scope).updater()
    }

    fun currentScope(updater: ScopeModifier.() -> Unit) {
      ScopeModifierImpl(currentScope() as PolySymbolsPsiScopeImpl).updater()
    }

    fun previousScope(updater: ScopeModifier.() -> Unit) {
      ScopeModifierImpl(previousScope() as PolySymbolsPsiScopeImpl).updater()
    }

    interface ScopeModifier {
      fun addSymbol(symbol: PolySymbol)
      fun addSymbols(symbol: List<PolySymbol>)
    }

    private inner class ScopeModifierImpl(private val scope: PolySymbolsPsiScopeImpl) : ScopeModifier {
      override fun addSymbol(symbol: PolySymbol) {
        if (symbol.qualifiedKind !in providedSymbolKinds)
          throw IllegalStateException("WebSymbol of kind ${symbol.qualifiedKind} should not be provided by ${this::class.java.name}")
        scope.add(symbol)
      }

      override fun addSymbols(symbol: List<PolySymbol>) {
        symbol.forEach { addSymbol(it) }
      }
    }
  }

  protected interface PolySymbolsPsiScope : PolySymbolsScope {
    val source: PsiElement
    val parent: PolySymbolsPsiScope?
    val properties: Map<String, Any>
    val children: List<PolySymbolsPsiScope>
    val localSymbols: List<PolySymbol>
    fun getAllSymbols(qualifiedKind: PolySymbolQualifiedKind): List<PolySymbol>
  }

  private class WebSymbolsPsiScopeWithPointer(
    private val delegate: PolySymbolsPsiScope,
    private val pointer: Pointer<out PolySymbolsPsiScope>,
  ) : PolySymbolsPsiScope by delegate {

    override fun createPointer(): Pointer<out PolySymbolsPsiScope> =
      pointer

    override fun equals(other: Any?): Boolean =
      other === this || delegate == other ||
      other is WebSymbolsPsiScopeWithPointer
      && other.delegate == delegate

    override fun hashCode(): Int =
      delegate.hashCode()
  }

  private class PolySymbolsPsiScopeImpl(
    override val source: PsiElement,
    override val properties: Map<String, Any>,
    override val parent: PolySymbolsPsiScopeImpl?,
    private val providedSymbolKinds: Set<PolySymbolQualifiedKind>,
    private val exclusiveSymbolKinds: Set<PolySymbolQualifiedKind>,
  ) : PolySymbolsPsiScope {

    override val children = ArrayList<PolySymbolsPsiScopeImpl>()
    override val localSymbols = SmartList<PolySymbol>()

    private val textRange: TextRange get() = source.textRange
    private val scopesInHierarchy get() = generateSequence(this) { it.parent }

    init {
      @Suppress("LeakingThis")
      parent?.add(this)
    }

    fun findBestMatchingScope(offset: Int): PolySymbolsPsiScope? {
      if (!textRange.contains(offset)) {
        return null
      }
      var curScope: PolySymbolsPsiScopeImpl? = null
      var innerScope: PolySymbolsPsiScopeImpl? = this
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
      params: WebSymbolsListSymbolsQueryParams,
      scope: Stack<PolySymbolsScope>,
    ): List<PolySymbolsScope> =
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

    private fun add(scope: PolySymbolsPsiScopeImpl) {
      children.add(scope)
    }

    override fun equals(other: Any?): Boolean =
      other is PolySymbolsPsiScopeImpl
      && other.source == source
      && other.textRange == textRange

    override fun hashCode(): Int =
      source.hashCode()

    override fun createPointer(): Pointer<out PolySymbolsScope> =
      throw IllegalStateException("WebSymbolsPsiScopeImpl cannot be pointed to. It should be wrapped with WebSymbolsPsiScopeWithPointer.")

    override fun getModificationCount(): Long = 0
  }

}