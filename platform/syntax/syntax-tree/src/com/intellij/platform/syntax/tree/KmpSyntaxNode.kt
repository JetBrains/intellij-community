// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.tree

import com.intellij.platform.syntax.*
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.extensions.ExtensionSupport
import com.intellij.platform.syntax.extensions.performWithExtensionSupport
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.lexer.tokenIndexAtOffset
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.util.cancellation.cancellationProvider
import com.intellij.platform.syntax.util.language.SyntaxElementLanguageProvider
import com.intellij.platform.syntax.util.language.getLanguage
import org.jetbrains.annotations.ApiStatus

/**
 * This factory type is used for specifying the builder for lazily parseable nodes
 * in the tree. All the parameters
 */
@ApiStatus.Experimental
fun interface SyntaxBuilderFactory {
  fun build(
    text: CharSequence,
    tokens: TokenList,
    startOffsetInParent: Int,
  ): SyntaxTreeBuilder
}

@ApiStatus.Experimental
class KmpSyntaxNode internal constructor(
  internal val parent: KmpSyntaxNode?,
  internal val prevSibling: KmpSyntaxNode?,
  internal val context: WalkerContext,
  val tokens: TokenList,
  internal val startLexemeIndex: Int,
  internal val nextMarkerStartLexemeIndex: Int,
  internal val markerIndex: Int,
  val languageProvider: SyntaxElementLanguageProvider,
) : SyntaxNode {
  companion object {
    private fun rootWithContext(
      context: WalkerContext,
      tokens: TokenList,
      languageProvider: SyntaxElementLanguageProvider,
    ): KmpSyntaxNode = KmpSyntaxNode(
      parent = null,
      prevSibling = null,
      context = context,
      tokens,
      startLexemeIndex = context.startLexemeIndex,
      nextMarkerStartLexemeIndex = context.startLexemeIndex,
      markerIndex = 0,
      languageProvider,
    )

    fun root(
      text: CharSequence,
      markers: ASTMarkers,
      lexer: Lexer,
      tokenizationPolicy: TokenizationPolicy,
      builderFactory: SyntaxBuilderFactory,
      tokens: TokenList,
      languageProvider: SyntaxElementLanguageProvider,
      extensions: () -> ExtensionSupport,
    ): KmpSyntaxNode = rootWithContext(
      WalkerContext(
        text = text,
        ast = markers,
        lexer = lexer,
        builderFactory = builderFactory,
        tokenizationPolicy = tokenizationPolicy,
        extensions = extensions,
      ),
      tokens,
      languageProvider,
    )
  }

  private fun copy(
    parent: KmpSyntaxNode? = this.parent,
    prevSibling: KmpSyntaxNode? = this.prevSibling,
    context: WalkerContext = this.context,
    startLexemeIndex: Int = this.startLexemeIndex,
    nextMarkerStartLexemeIndex: Int = this.nextMarkerStartLexemeIndex,
    markerIndex: Int = this.markerIndex,
  ): KmpSyntaxNode = KmpSyntaxNode(
    parent = parent,
    prevSibling = prevSibling,
    context = context,
    startLexemeIndex = startLexemeIndex,
    nextMarkerStartLexemeIndex = nextMarkerStartLexemeIndex,
    tokens = tokens,
    markerIndex = markerIndex,
    languageProvider = languageProvider,
  )

  internal val isMarker = markerIndex != -1 && startLexemeIndex == nextMarkerStartLexemeIndex
  internal val endLexemeIndex = startLexemeIndex + (if (isMarker) context.ast.lexemeCount(markerIndex) else 1)

  override val type: SyntaxElementType get() = elementType

  val elementType: SyntaxElementType = when {
    isMarker -> context.ast.elementType(markerIndex)
    else -> tokens.getTokenType(startLexemeIndex)!!
  }

  override val language: SyntaxLanguage? by lazy {
    (languageProvider.getLanguage(elementType) ?: parent?.language)!!
  }

  override fun equals(other: Any?): Boolean =
    (other === this) || (other is KmpSyntaxNode &&
                         other.tokens == tokens &&
                         other.markerIndex == markerIndex &&
                         other.startLexemeIndex == startLexemeIndex)

  override fun hashCode(): Int = arrayOf(
    context, markerIndex, startLexemeIndex
  ).contentHashCode()

  override val text: CharSequence
    get() = context.text.subSequence(
      (startOffset - context.offset),
      (endOffset - context.offset)
    )

  override val startOffset: Int
    get() = context.offset + when {
      startLexemeIndex == tokens.tokenCount -> context.text.length
      else -> tokens.getTokenStart(startLexemeIndex)
    }

  override val endOffset: Int
    get() = context.offset + when {
      isMarker -> when (context.ast.kind(markerIndex)) {
        MarkerKind.Start, MarkerKind.Error ->
          tokens.endCharAt(endLexemeIndex - 1)

        MarkerKind.End -> error("should not be at the end")
        else -> error("no else")
      }

      else -> tokens.endCharAt(startLexemeIndex)
    }

  override val errorMessage: String?
    get() =
      (if (isMarker) context.ast.errorMessage(markerIndex) else null)
      ?: (if (type == SyntaxTokenTypes.ERROR_ELEMENT) "Bad token" else null)


  override fun parent(): SyntaxNode? = parent

  override fun firstChild(): SyntaxNode? = when {
    isMarker -> {
      val ast = context.ast
      when {
        ast.kind(markerIndex) == MarkerKind.Error -> null
        isChameleon() -> (chameleonSyntaxNode?.firstChild() as KmpSyntaxNode?)
          ?.copy(parent = this)

        else -> {
          val childMarkerIndex = ast.firstChild(markerIndex)
          when {
            childMarkerIndex == -1 && startLexemeIndex != endLexemeIndex -> copy(
              markerIndex = -1,
              nextMarkerStartLexemeIndex = startLexemeIndex,
              prevSibling = null,
              parent = this
            )

            childMarkerIndex == -1 -> null
            ast.kind(childMarkerIndex) == MarkerKind.Start || ast.kind(childMarkerIndex) == MarkerKind.Error -> copy(
              markerIndex = childMarkerIndex,
              prevSibling = null,
              nextMarkerStartLexemeIndex = startLexemeIndex + ast.lexemeRelOffset(childMarkerIndex),
              parent = this
            )


            ast.kind(childMarkerIndex) == MarkerKind.End -> error("should not be at the end")
            else -> error("no else")
          }
        }
      }
    }

    isChameleon() && !isCopyOfParent() -> (chameleonSyntaxNode?.firstChild() as KmpSyntaxNode?)
      ?.copy(parent = this)

    else -> null
  }

  private fun isCopyOfParent(): Boolean =
    parent != null &&
    parent.type == this.type &&
    parent.startLexemeIndex == this.startLexemeIndex &&
    parent.endLexemeIndex == this.endLexemeIndex &&
    parent.markerIndex == this.markerIndex

  internal fun isChameleon(): Boolean = (isMarker &&
                                         context.ast.kind(markerIndex) == MarkerKind.Start &&
                                         context.ast.elementType(markerIndex).isLazyParseable() &&
                                         context.ast.collapsed(markerIndex)) ||
                                        (!isMarker && type.isLazyParseable())

  override fun lastChild(): SyntaxNode? = firstChild().lastSibling()

  override fun nextSibling(): SyntaxNode? = when {
    parent == null -> null
    isMarker -> {
      val siblingMarkerIndex = context.ast.nextSibling(markerIndex)
      val startLexemeIndex = when {
        siblingMarkerIndex == -1 -> endLexemeIndex
        else -> endLexemeIndex + context.ast.lexemeRelOffset(siblingMarkerIndex)
      }
      goForthToNextSibling(
        siblingLexemeIndex = endLexemeIndex,
        startLexemeIndex = startLexemeIndex,
        markerIndex = siblingMarkerIndex
      )
    }

    else ->
      goForthToNextSibling(
        siblingLexemeIndex = startLexemeIndex + 1,
        startLexemeIndex = when {
          markerIndex != -1 -> nextMarkerStartLexemeIndex
          else -> startLexemeIndex + 1
        },
        markerIndex = markerIndex
      )
  }

  override fun prevSibling(): SyntaxNode? = prevSibling

  private fun goForthToNextSibling(
    siblingLexemeIndex: Int,
    startLexemeIndex: Int,
    markerIndex: Int,
  ): KmpSyntaxNode? {
    // do we change token set in chameleon node?
    val parentEndLexeme = if (parent?.tokens != tokens) tokens.tokenCount else parent.endLexemeIndex
    return when {
      markerIndex != -1 || siblingLexemeIndex < parentEndLexeme -> copy(
        startLexemeIndex = siblingLexemeIndex,
        nextMarkerStartLexemeIndex = startLexemeIndex,
        markerIndex = markerIndex,
        prevSibling = this
      )

      else -> null
    }
  }

  override fun childByOffset(offset: Int): SyntaxNode? = children()
    .firstOrNull { offset in it.startOffset until it.endOffset }

  private val chameleonSyntaxNode: SyntaxNode?
    get() {
      val (chameleonTokens, tree) = getOrParseChameleon()
      return when {
        tree.size == 0 -> null
        chameleonTokens == null -> rootWithContext(
          context.copy(
            ast = tree,
            startLexemeIndex = startLexemeIndex
          ),
          tokens,
          languageProvider,
        )

        else -> {
          val startOffsetInContext: Int = tokens.getTokenStart(startLexemeIndex)
          val endOffsetInContext = tokens.endCharAt(endLexemeIndex - 1)
          val chameleonText = context.text.subSequence(startOffsetInContext, endOffsetInContext)

          val newContextLexer = performWithExtensionSupport(context.extensions()) {
            val lexingContext = LazyLexingContext(this@KmpSyntaxNode, cancellationProviderOrNoop())
            createLexer(lexingContext) ?: context.lexer
          }
          rootWithContext(
            context.copy(
              text = chameleonText,
              lexer = newContextLexer,
              startLexemeIndex = 0,
              offset = startOffset,
              ast = tree,
            ),
            chameleonTokens,
            languageProvider,
          )
        }
      }
    }

  private fun getOrParseChameleon(): AstMarkersChameleon {
    val id = startLexemeIndex - context.startLexemeIndex
    val c = try {
      context.ast.chameleonAt(id)
    }
    catch (_: NullPointerException) {
      error("Chameleon at $id not found.")
    }
    return c.realize {
      parseChameleon(
        text = context.text,
        contextLexer = context.lexer,
        lexemeStore = tokens,
        builderFactory = context.builderFactory,
        startLexeme = startLexemeIndex,
        lexemeCount = endLexemeIndex - startLexemeIndex,
        cancellationProvider = performWithExtensionSupport(context.extensions()) { cancellationProviderOrNoop() },
      )
    }
  }

  private fun parseChameleon(
    text: CharSequence,
    contextLexer: Lexer,
    lexemeStore: TokenList,
    builderFactory: SyntaxBuilderFactory,
    startLexeme: Int,
    lexemeCount: Int,
    cancellationProvider: CancellationProvider,
  ): AstMarkersChameleon {
    val node = this@KmpSyntaxNode
    val lexingContext = LazyLexingContext(node, cancellationProvider)
    val lexer = performWithExtensionSupport(context.extensions()) {
      createLexer(lexingContext)
    }
    val chameleonText: CharSequence
    val chameleonTokens: TokenList
    val lexerChanged = lexer != null && lexer != contextLexer
    when {
      lexerChanged -> {
        val startOffsetInContext = lexemeStore.getTokenStart(startLexeme)
        val endOffsetInContext = lexemeStore.endCharAt(startLexeme + lexemeCount - 1)

        chameleonText = text.subSequence(startOffsetInContext, endOffsetInContext)
        chameleonTokens = context.tokenizationPolicy.tokenize(
          text = chameleonText,
          lexer = lexer,
          cancellationProvider = cancellationProvider,
        )
      }

      else -> {
        chameleonText = text
        chameleonTokens = lexemeStore.slice(startLexeme, startLexeme + lexemeCount)
      }
    }
    val builder = builderFactory.build(
      text = chameleonText,
      tokens = chameleonTokens,
      startOffsetInParent = startOffset
    )

    val lazyParsingContext = LazyParsingContext(
      node = node,
      tokenList = chameleonTokens,
      syntaxTreeBuilder = builder,
      cancellationProvider = cancellationProvider
    )

    performWithExtensionSupport(context.extensions()) {
      parseLazyNode(lazyParsingContext)
    }
    return AstMarkersChameleon(
      chameleonTokens.takeIf { lexerChanged },
      builder.toAstMarkers()
    )
  }

  @Suppress("UNUSED")
  fun reportState(): String = buildString {
    append(
      """
            context offset : ${context.offset}
            context startLexemeIndex: ${context.startLexemeIndex}
            file text :
            ```
            ${context.text}
            ```
            ast tree :
            ```
            ${context.ast}
            ```
            """.trimIndent()
    )
    var node: KmpSyntaxNode? = this@KmpSyntaxNode
    while (node != null) {
      appendLine(node)
      node = node.parent
    }
  }

  override fun toString(): String =
    "$type [m=$markerIndex, l=$startLexemeIndex], (sl=$nextMarkerStartLexemeIndex, el=$endLexemeIndex) " +
    if (isMarker) "mc=" + context.ast.markersCount(markerIndex) else ""

  private fun findDeepestReparseableNode(
    rootLexer: Lexer,
    newTokens: TokenList,
    newText: CharSequence,
    startOffset: Long,
    endOffset: Long,
    cancellationProvider: CancellationProvider,
    builderFactory: SyntaxBuilderFactory,
  ): Triple<KmpSyntaxNode, TokenList, Boolean>? {
    fun KmpSyntaxNode.asReparseableNode(newTokens: TokenList): KmpSyntaxNode? {
      val newType = when {
        startOffset >= 0 && startOffset < newTokens.tokenizedText.length -> {
          newTokens.getTokenType(
            newTokens
              .tokenIndexAtOffset(startOffset.toInt())
              .onMinusOne(newTokens.tokenCount - 1)
          )
        }

        else -> null
      }
      return when {
        newType == type -> this
        else -> null
      }
    }

    var node: KmpSyntaxNode? = this
    var currentTokens = newTokens
    var nodeTokens = currentTokens
    var deepestReparseableNode: KmpSyntaxNode? = null

    var currentLexer = rootLexer
    while (node != null && node.startOffset <= startOffset && node.endOffset > endOffset) {
      val type = node.type
      if (type.isLazyParseable()) {
        val nodeText = newText.subSequence(node.startOffset, node.endOffset)
        val startLexemeIndex = currentTokens
          .tokenIndexAtOffset((node.startOffset - node.context.offset))
          .onMinusOne(currentTokens.tokenCount - 1)
        val endLexemeIndex = currentTokens
          .tokenIndexAtOffset((node.endOffset - node.context.offset))
          .onMinusOne(currentTokens.tokenCount - 1)

        val slice = currentTokens.slice(startLexemeIndex, endLexemeIndex)
        val parsingContext = LazyParsingContext(
          node = node,
          tokenList = slice,
          syntaxTreeBuilder = builderFactory.build(nodeText, slice, node.startOffset),
          cancellationProvider = cancellationProvider
        )
        if (performWithExtensionSupport(context.extensions()) { canLazyNodeBeReparsedIncrementally(parsingContext) }) {
          deepestReparseableNode = node
          nodeTokens = currentTokens
        }
        else {
          val remapped = nodeTokens
          val reparseableNode = deepestReparseableNode?.asReparseableNode(remapped) ?: return null
          return Triple(
            reparseableNode,
            remapped,
            currentLexer != rootLexer
          )
        }

        val lexingContext = LazyLexingContext(node, cancellationProvider)
        val lexer = performWithExtensionSupport(context.extensions()) {
          createLexer(lexingContext)
        }
        if (lexer != null && lexer != currentLexer) {
          currentLexer = lexer
          currentTokens = context.tokenizationPolicy.tokenize(
            text = nodeText,
            lexer = lexer,
            cancellationProvider = cancellationProvider,
          )
        }
      }
      node = node.children()
        .firstOrNull { it.startOffset <= startOffset && it.endOffset >= endOffset } as KmpSyntaxNode?
    }
    val reparseableNode = deepestReparseableNode?.asReparseableNode(nodeTokens) ?: return null
    return Triple(reparseableNode, nodeTokens, currentLexer != rootLexer)
  }

  fun tryReparse(
    builderFactory: SyntaxBuilderFactory,
    lexer: Lexer,
    newTokens: TokenList,
    newText: CharSequence,
    startOffset: Long,
    endOffset: Long,
    cancellationProvider: CancellationProvider,
  ): Pair<ASTMarkers, TokenList>? {
    val (reparseableNode, nodeTokens, isLexerChanged) = this.findDeepestReparseableNode(
      rootLexer = lexer,
      newTokens = newTokens,
      newText = newText,
      startOffset = startOffset,
      endOffset = newText.length - endOffset,
      builderFactory = builderFactory,
      cancellationProvider = cancellationProvider,
    ) ?: return null

    val startLexemeIndex = nodeTokens.tokenIndexAtOffset(
      (reparseableNode.startOffset - reparseableNode.context.offset)
    ).onMinusOne(nodeTokens.tokenCount - 1)
    val endLexemeIndex = nodeTokens.tokenIndexAtOffset(
      (reparseableNode.endOffset - reparseableNode.context.offset)
    ).onMinusOne(nodeTokens.tokenCount - 1)
    val oldLexemeCount = endLexemeIndex - startLexemeIndex

    val diff = nodeTokens.tokenCount - reparseableNode.tokens.tokenCount
    for (i in 0 until startLexemeIndex) {
      nodeTokens.remap(i, reparseableNode.tokens.getTokenType(i)!!)
    }

    for (i in endLexemeIndex..<nodeTokens.tokenCount) {
      nodeTokens.remap(i, reparseableNode.tokens.getTokenType(i - diff)!!)
    }

    val (lexemeStore, node) = reparseableNode.parseChameleon(
      text = newText,
      contextLexer = lexer,
      lexemeStore = nodeTokens,
      builderFactory = builderFactory,
      startLexeme = startLexemeIndex,
      lexemeCount = oldLexemeCount,
      cancellationProvider = cancellationProvider,
    )

    val newLexemeStorage = nodeTokens.takeUnless { isLexerChanged } ?: newTokens
    val astMarkers = substitute(
      syntaxNode = reparseableNode,
      nodeTokens = nodeTokens,
      parsedChameleon = AstMarkersChameleon(lexemeStore, node)
    ) ?: return null

    return astMarkers to newLexemeStorage
  }
}

private fun Int.onMinusOne(defaultValue: Int) = if (this == -1) defaultValue else this

internal data class WalkerContext(
  val text: CharSequence,
  val ast: ASTMarkers,
  val lexer: Lexer,
  val builderFactory: SyntaxBuilderFactory,
  val tokenizationPolicy: TokenizationPolicy,
  val startLexemeIndex: Int = 0,
  val offset: Int = 0,
  val extensions: () -> ExtensionSupport,
)

internal tailrec fun SyntaxNode?.lastSibling(): SyntaxNode? = when (val s = this?.nextSibling()) {
  null -> this
  else -> s.lastSibling()
}

private fun TokenList.endCharAt(index: Int): Int = when {
  index >= tokenCount -> error("index out of bounds")
  index == tokenCount - 1 -> tokenizedText.length
  index == -1 -> getTokenStart(0)
  else -> getTokenEnd(index)
}

@ApiStatus.Experimental
fun interface TokenizationPolicy {
  fun tokenize(
    text: CharSequence,
    lexer: Lexer,
    cancellationProvider: CancellationProvider,
  ): TokenList
}

private fun substitute(
  syntaxNode: KmpSyntaxNode,
  nodeTokens: TokenList,
  parsedChameleon: AstMarkersChameleon,
): ASTMarkers? {
  var newNodeTokensAdded = false
  var parent = syntaxNode.parent
  val newLexemeCount = parsedChameleon.ast.lexemeCount(0)
  val oldLexemeCount = syntaxNode.endLexemeIndex - syntaxNode.nextMarkerStartLexemeIndex
  var diff = if (parsedChameleon.customLexemeStore == null) newLexemeCount - oldLexemeCount else 0
  while (parent?.context == syntaxNode.context) {
    parent = parent.parent
  }

  val pairs = syntaxNode.context.ast.chameleons().map { (key, value) ->
    val contextKey = key + syntaxNode.context.startLexemeIndex
    val newKey = if (contextKey >= syntaxNode.endLexemeIndex) key + diff else key
    if (contextKey == syntaxNode.startLexemeIndex) {
      newKey to newChameleonRef(parsedChameleon)
    }
    else {
      newKey to value
    }
  }
  var reparsedAst = syntaxNode.context.ast.mutate {
    changeChameleons(pairs)
    if (syntaxNode.isChameleon()) {
      if (syntaxNode.isMarker && parsedChameleon.customLexemeStore == null) {
        updateLexemes(syntaxNode, diff)
      }
    }
    else {
      updateLexemesAndMarkers(parsedChameleon, syntaxNode, diff)
      substitute(
        syntaxNode.markerIndex,
        syntaxNode.startLexemeIndex - syntaxNode.context.startLexemeIndex,
        parsedChameleon.ast
      )
    }
  }

  while (parent != null) {
    if (parent.isChameleon()) {
      val replacedChameleon = parent.context.ast.chameleons()
        .sortedBy { (lexemeIndex, _) -> lexemeIndex }
        .map { (lexemeIndex, ref) ->
          when {
            lexemeIndex < parent.startLexemeIndex - parent.context.startLexemeIndex -> lexemeIndex to ref
            lexemeIndex == parent.startLexemeIndex - parent.context.startLexemeIndex -> {
              //todo[jetzajac]: no idea what to do if parent chameleon is collected, just give up the "reparse"
              val oldParsedChameleon = ref.value ?: return null
              val theSameTokens = oldParsedChameleon.customLexemeStore == null
              val newTokens = if (!theSameTokens && !newNodeTokensAdded) {
                newNodeTokensAdded = true
                nodeTokens
              }
              else oldParsedChameleon.customLexemeStore
              val newParsedChameleon = AstMarkersChameleon(newTokens, ast = reparsedAst)
              diff = if (theSameTokens) diff else 0
              lexemeIndex to newChameleonRef(newParsedChameleon)
            }

            else -> (lexemeIndex + diff) to ref
          }
        }
      reparsedAst = parent.context.ast.mutate {
        changeChameleons(replacedChameleon)
        if (parent!!.isMarker && diff != 0) {
          updateLexemes(parent!!, diff)
        }
      }
    }
    parent = parent.parent
  }
  return reparsedAst
}

private fun ASTMarkers.MutableContext.updateLexemesAndMarkers(
  parsedChameleon: AstMarkersChameleon,
  reparseableNode: KmpSyntaxNode,
  diff: Int,
) {
  updateLexemes(reparseableNode, diff)
  val newMarkers = parsedChameleon.ast.markersCount(0)
  val oldMarkers = reparseableNode.context.ast.markersCount(reparseableNode.markerIndex)
  var node: KmpSyntaxNode? = reparseableNode.parent
  while (node != null && node.context.ast == reparseableNode.context.ast) {
    val startMarkerIndex = node.markerIndex
    val endMarkerIndex = startMarkerIndex + node.context.ast.markersCount(node.markerIndex)
    val prevMarkersCount = node.context.ast.markersCount(startMarkerIndex)
    changeMarkerCount(startMarkerIndex, endMarkerIndex, prevMarkersCount - oldMarkers + newMarkers)
    node = node.parent
  }
  changeMarkerCount(
    0,
    reparseableNode.context.ast.markersCount(0),
    reparseableNode.context.ast.markersCount(0) - oldMarkers + newMarkers
  )
}

private fun ASTMarkers.MutableContext.updateLexemes(
  reparseableNode: KmpSyntaxNode,
  diff: Int,
) {
  var node: KmpSyntaxNode? = reparseableNode
  while (node != null && node.context.ast == reparseableNode.context.ast) {
    updateLexemeCount(node.markerIndex, node.context.ast, diff)
    node = node.parent
  }
  updateLexemeCount(0, reparseableNode.context.ast, diff)
}

private fun ASTMarkers.MutableContext.updateLexemeCount(
  startMarkerIndex: Int,
  ast: ASTMarkers,
  diff: Int,
) {
  val endMarkerIndex = startMarkerIndex + ast.markersCount(startMarkerIndex)
  val prevLexCount = ast.lexemeCount(startMarkerIndex)
  changeLexCount(startMarkerIndex, endMarkerIndex, prevLexCount + diff)
}

private fun cancellationProviderOrNoop(): CancellationProvider = cancellationProvider() ?: CancellationProvider {}