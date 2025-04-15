package com.intellij.webSymbols.utils

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
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.query.WebSymbolsCompoundScope
import com.intellij.webSymbols.query.WebSymbolsListSymbolsQueryParams
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor

abstract class WebSymbolsStructuredScope<T : PsiElement, R : PsiElement>(protected val location: T) : WebSymbolsCompoundScope() {

  protected abstract val rootPsiElement: R?

  protected abstract val scopesBuilderProvider: (rootPsiScope: R, holder: WebSymbolsPsiScopesHolder) -> PsiElementVisitor?

  protected abstract val providedSymbolKinds: Set<WebSymbolQualifiedKind>

  override fun build(queryExecutor: WebSymbolsQueryExecutor, consumer: (WebSymbolsScope) -> Unit) {
    getCurrentScope()
      ?.let { consumer(it) }
  }

  protected fun getCurrentScope(): WebSymbolsPsiScope? =
    getRootScope()
      ?.let { findBestMatchingScope(it) }
      ?.let {
        val structuredScopePtr = this@WebSymbolsStructuredScope.createPointer()
        WebSymbolsPsiScopeWithPointer(it) {
          structuredScopePtr.dereference()?.getCurrentScope()
        }
      }

  protected fun getRootScope(): WebSymbolsPsiScope? {
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

  abstract override fun createPointer(): Pointer<out WebSymbolsStructuredScope<T, R>>

  override fun equals(other: Any?): Boolean =
    other === this || (
      other is WebSymbolsStructuredScope<*, *>
      && other.javaClass === this.javaClass
      && other.location == location)

  override fun hashCode(): Int =
    location.hashCode()

  protected open fun findBestMatchingScope(rootScope: WebSymbolsPsiScope): WebSymbolsPsiScope? =
    (rootScope as WebSymbolsPsiScopeImpl).findBestMatchingScope(location.textOffset)

  protected class WebSymbolsPsiScopesHolder(val rootElement: PsiElement, val providedSymbolKinds: Set<WebSymbolQualifiedKind>) {
    private val scopes = Stack<WebSymbolsPsiScope>()

    internal val topLevelScope: WebSymbolsPsiScope
      get() {
        assert(scopes.size == 1)
        return scopes.peek()
      }

    init {
      scopes.add(WebSymbolsPsiScopeImpl(rootElement, emptyMap(), null, providedSymbolKinds, emptySet()))
    }

    fun currentScope(): WebSymbolsPsiScope =
      scopes.peek()

    fun previousScope(): WebSymbolsPsiScope =
      scopes[scopes.size - 2]

    fun popScope() {
      scopes.pop()
    }

    fun pushScope(
      scopePsiElement: PsiElement,
      properties: Map<String, Any> = emptyMap(),
      exclusiveSymbolKinds: Set<WebSymbolQualifiedKind> = emptySet(),
      updater: (ScopeModifier.() -> Unit)? = null,
    ) {
      val scope = WebSymbolsPsiScopeImpl(scopePsiElement, properties,
                                         currentScope() as WebSymbolsPsiScopeImpl,
                                         providedSymbolKinds, exclusiveSymbolKinds)
      scopes.push(scope)
      if (updater != null) ScopeModifierImpl(scope).updater()
    }

    fun currentScope(updater: ScopeModifier.() -> Unit) {
      ScopeModifierImpl(currentScope() as WebSymbolsPsiScopeImpl).updater()
    }

    fun previousScope(updater: ScopeModifier.() -> Unit) {
      ScopeModifierImpl(previousScope() as WebSymbolsPsiScopeImpl).updater()
    }

    interface ScopeModifier {
      fun addSymbol(symbol: WebSymbol)
      fun addSymbols(symbol: List<WebSymbol>)
    }

    private inner class ScopeModifierImpl(private val scope: WebSymbolsPsiScopeImpl) : ScopeModifier {
      override fun addSymbol(symbol: WebSymbol) {
        if (symbol.qualifiedKind !in providedSymbolKinds)
          throw IllegalStateException("WebSymbol of kind ${symbol.qualifiedKind} should not be provided by ${this::class.java.name}")
        scope.add(symbol)
      }

      override fun addSymbols(symbol: List<WebSymbol>) {
        symbol.forEach { addSymbol(it) }
      }
    }
  }

  protected interface WebSymbolsPsiScope : WebSymbolsScope {
    val source: PsiElement
    val parent: WebSymbolsPsiScope?
    val properties: Map<String, Any>
    val children: List<WebSymbolsPsiScope>
    val localSymbols: List<WebSymbol>
    fun getAllSymbols(qualifiedKind: WebSymbolQualifiedKind): List<WebSymbol>
  }

  private class WebSymbolsPsiScopeWithPointer(
    private val delegate: WebSymbolsPsiScope,
    private val pointer: Pointer<out WebSymbolsPsiScope>,
  ) : WebSymbolsPsiScope by delegate {

    override fun createPointer(): Pointer<out WebSymbolsPsiScope> =
      pointer

    override fun equals(other: Any?): Boolean =
      other === this || delegate == other ||
      other is WebSymbolsPsiScopeWithPointer
      && other.delegate == delegate

    override fun hashCode(): Int =
      delegate.hashCode()
  }

  private class WebSymbolsPsiScopeImpl(
    override val source: PsiElement,
    override val properties: Map<String, Any>,
    override val parent: WebSymbolsPsiScopeImpl?,
    private val providedSymbolKinds: Set<WebSymbolQualifiedKind>,
    private val exclusiveSymbolKinds: Set<WebSymbolQualifiedKind>,
  ) : WebSymbolsPsiScope {

    override val children = ArrayList<WebSymbolsPsiScopeImpl>()
    override val localSymbols = SmartList<WebSymbol>()

    private val textRange: TextRange get() = source.textRange
    private val scopesInHierarchy get() = generateSequence(this) { it.parent }

    init {
      @Suppress("LeakingThis")
      parent?.add(this)
    }

    fun findBestMatchingScope(offset: Int): WebSymbolsPsiScope? {
      if (!textRange.contains(offset)) {
        return null
      }
      var curScope: WebSymbolsPsiScopeImpl? = null
      var innerScope: WebSymbolsPsiScopeImpl? = this
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

    override fun isExclusiveFor(qualifiedKind: WebSymbolQualifiedKind): Boolean =
      scopesInHierarchy.any { it.exclusiveSymbolKinds.contains(qualifiedKind) }

    override fun getSymbols(
      qualifiedKind: WebSymbolQualifiedKind,
      params: WebSymbolsListSymbolsQueryParams,
      scope: Stack<WebSymbolsScope>,
    ): List<WebSymbolsScope> =
      getAllSymbols(qualifiedKind)

    override fun getAllSymbols(qualifiedKind: WebSymbolQualifiedKind): List<WebSymbol> =
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

    fun add(symbol: WebSymbol) {
      localSymbols.add(symbol)
    }

    private fun add(scope: WebSymbolsPsiScopeImpl) {
      children.add(scope)
    }

    override fun equals(other: Any?): Boolean =
      other is WebSymbolsPsiScopeImpl
      && other.source == source
      && other.textRange == textRange

    override fun hashCode(): Int =
      source.hashCode()

    override fun createPointer(): Pointer<out WebSymbolsScope> =
      throw IllegalStateException("WebSymbolsPsiScopeImpl cannot be pointed to. It should be wrapped with WebSymbolsPsiScopeWithPointer.")

    override fun getModificationCount(): Long = 0
  }

}