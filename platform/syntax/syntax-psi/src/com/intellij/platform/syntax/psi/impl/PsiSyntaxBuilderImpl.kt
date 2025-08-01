// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.UnprotectedUserDataHolder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.syntax.LanguageSyntaxDefinition
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.parser.*
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory.builder
import com.intellij.platform.syntax.psi.*
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.BlockSupportImpl
import com.intellij.psi.impl.DiffLog
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.impl.source.tree.LazyParseableElement
import com.intellij.psi.text.BlockSupport
import com.intellij.psi.text.BlockSupport.ReparsedSuccessfullyException
import com.intellij.psi.tree.CustomLanguageASTComparator
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.ILazyParseableElementTypeBase
import com.intellij.util.CharTable
import com.intellij.util.ThreeState
import com.intellij.util.TripleFunction
import com.intellij.util.diff.FlyweightCapableTreeStructure
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.NonNls
import java.util.*
import kotlin.math.max

internal class PsiSyntaxBuilderImpl(
  internal var file: PsiFile?,
  private val parserDefinition: ParserDefinition,
  syntaxDefinition: LanguageSyntaxDefinition,
  internal var charTable: CharTable?,
  private val text: CharSequence,
  private val originalTree: ASTNode?,
  private val lastCommittedText: CharSequence?,
  private val parentLightTree: FlyweightCapableTreeStructure<LighterASTNode>?,
  internal val startOffset: Int,
  private val tokenList: TokenList,
  private val tokenConverter: ElementTypeConverter,
  opaquePolicy: OpaqueElementPolicy?,
  whitespaceOrCommentBindingPolicy: WhitespaceOrCommentBindingPolicy?,
) : UnprotectedUserDataHolder(), PsiSyntaxBuilder {

  internal val builder: SyntaxTreeBuilder = builder(
    text = text,
    whitespaces = syntaxDefinition.whitespaces,
    comments = syntaxDefinition.comments,
    tokenList = tokenList,
  )
    .withStartOffset(startOffset)
    .withDebugMode(false)
    .withLanguage(this.file?.getLanguage()?.toString())
    .withCancellationProvider { ProgressManager.checkCanceled() }
    .withWhitespaceOrCommentBindingPolicy(whitespaceOrCommentBindingPolicy)
    .withOpaquePolicy(opaquePolicy)
    .withLogger(syntaxTreeBuilderLogger)
    .build()

  internal val textArray: CharArray? = CharArrayUtil.fromSequenceWithoutCopying(text)

  private var customComparator: TripleFunction<ASTNode, LighterASTNode, FlyweightCapableTreeStructure<LighterASTNode>, ThreeState>? = null

  private var productionResult: ProductionResult? = null

  init {
    require((originalTree == null) == (lastCommittedText == null)) {
      val trimmedLastCommittedText = lastCommittedText?.let { "'" + StringUtil.first(lastCommittedText, 80, true) + "'" }
      "originalTree and lastCommittedText must be null/notnull together but got: originalTree=$originalTree; lastCommittedText=$trimmedLastCommittedText"
    }
  }

  override fun getSyntaxTreeBuilder(): SyntaxTreeBuilder = builder

  private val originalText: CharSequence
    get() = builder.text

  override fun getTreeBuilt(): ASTNode {
    val rootMarker = prepareLightTree()
    val file = this.file
    val possiblyTooDeep = file != null && BlockSupport.isTooDeep(file.getOriginalFile())

    if (originalTree != null && !possiblyTooDeep) {
      val diffLog = merge(originalTree, rootMarker, lastCommittedText!!)
      throw ReparsedSuccessfullyException(diffLog)
    }

    val data = rootMarker.nodeData
    val rootNode = data.createRootAST(rootMarker)
    data.bind(rootMarker, rootNode as CompositeElement)

    if (possiblyTooDeep && rootNode !is FileElement) {
      val childNode: ASTNode? = rootNode.firstChildNode
      childNode?.putUserData(BlockSupport.TREE_DEPTH_LIMIT_EXCEEDED, true)
    }

    val text = originalText
    if (LOG.isDebugEnabled() && rootNode.getTextLength() != text.length) {
      LOG.error("Inconsistent root node. " +
                "; node type: " + rootNode.elementType +
                "; text length: " + text.length +
                "; node length: " + rootNode.getTextLength() +
                "; partial text: " + StringUtil.shortenTextWithEllipsis(text.toString(), 512, 256) +
                "; partial node text: " + StringUtil.shortenTextWithEllipsis(rootNode.getText(), 512, 256)
      )
    }

    return rootNode
  }

  override fun getLightTree(): FlyweightCapableTreeStructure<LighterASTNode> {
    val rootMarker = prepareLightTree()
    return MyTreeStructure(rootMarker, parentLightTree as? MyTreeStructure)
  }

  override fun setDebugMode(value: Boolean) {
    builder.setDebugMode(value)
  }

  override fun setCustomComparator(comparator: TripleFunction<ASTNode, LighterASTNode, FlyweightCapableTreeStructure<LighterASTNode>, ThreeState>) {
    customComparator = comparator
  }

  private fun merge(oldRoot: ASTNode, newRoot: CompositeNode, lastCommittedText: CharSequence): DiffLog {
    val diffLog = DiffLog()
    val builder = ConvertFromTokensToASTBuilder(newRoot, diffLog)
    val treeStructure = MyTreeStructure(newRoot, null)
    val customLanguageASTComparators = CustomLanguageASTComparator.getMatchingComparators(this.file!!)
    val comparator = MyComparator(treeStructure, customLanguageASTComparators, customComparator)
    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()
    BlockSupportImpl.diffTrees(oldRoot, builder, comparator, treeStructure, indicator, lastCommittedText)
    return diffLog
  }

  private fun createMarker(
    productionMarker: SyntaxTreeBuilder.Production,
    parent: CompositeNode?,
    nodeData: NodeData,
    markedId: Int,
  ): CompositeNode {
    val errorMessage = productionMarker.getErrorMessage()
    if (errorMessage != null) {
      nodeData.optionalData.setErrorMessage(markedId, errorMessage)
    }
    return CompositeNode(
      markerId = markedId,
      myType = tokenConverter.convertNotNull(productionMarker.getNodeType()),
      startIndex = productionMarker.getStartTokenIndex(),
      myEndIndex = productionMarker.getEndTokenIndex(),
      data = nodeData,
      parent = parent
    )
  }

  private fun createErrorNode(
    item: SyntaxTreeBuilder.Production,
    parent: CompositeNode,
    nodeData: NodeData,
    markedId: Int,
  ): ErrorNode {
    val error = ErrorNode(
      markerId = markedId,
      index = item.getStartTokenIndex(),
      data = nodeData,
      parent = parent,
      message = item.getErrorMessage()!!
    )
    return error
  }


  private fun prepareLightTree(): CompositeNode {
    // todo cache the tree here
    val productionResult = prepareProduction(builder)
    this.productionResult = productionResult

    return buildTreeFromProduction(productionResult)
  }

  private fun buildTreeFromProduction(
    productionResult: ProductionResult,
  ): CompositeNode {
    val productions = productionResult.productionMarkers
    val tokenCount = productionResult.tokenSequence.tokenCount
    val starts = IntArray(tokenCount + 1) // todo invent a better way to do this. we use +1 item, to store the end offset of the last token, basically it contains text.length
    productionResult.copyTokenStartsToArray(starts, 0, 0, tokenCount + 1)

    @Suppress("UNCHECKED_CAST")
    val originalLexTypes = arrayOfNulls<SyntaxElementType>(tokenCount) as Array<SyntaxElementType>
    productionResult.copyTokenTypesToArray(originalLexTypes, 0, 0, tokenCount)
    val lexTypes = tokenConverter.convert(originalLexTypes)
    assertAllElementsConverted(lexTypes, originalLexTypes)

    val compositeOptionalData = CompositeOptionalData()

    val nodeData = NodeData(
      lexStarts = starts,
      offset = startOffset,
      optionalData = compositeOptionalData,
      text = text,
      whitespaceTokens = parserDefinition.whitespaceTokens,
      lexemeCount = tokenCount,
      lexTypes = originalLexTypes,
      convertedLexTypes = lexTypes as Array<IElementType>,
      charTable = this.charTable,
      astFactory = parserDefinition as? ASTFactory,
      textArray = this.textArray,
      file = this.file,
    )
    val rootProductionMarker = productions.getMarker(0)
    val rootMarker = createMarker(rootProductionMarker, null, nodeData, 0)
    val nodes = ArrayDeque<Pair<CompositeNode, SyntaxTreeBuilder.Production>>()
    nodes.addLast(rootMarker to rootProductionMarker)

    var curNode = rootMarker
    var curProduction = rootProductionMarker

    var lastErrorIndex = -1
    var maxDepth = 0
    var curDepth = 0
    var i = 1
    val size = productions.size
    while (i < size) {
      val isDone = productions.isDoneMarker(i)

      if (isDone) {
        // done marker, id < 0
        assertMarkersBalanced(productionResult.productionMarkers.getMarker(i) === curProduction, null)

        val pair = nodes.removeLast()
        curNode = pair.first
        curProduction = pair.second
        curDepth--
      }
      else {
        val item = productions.getMarker(i)
        if (item.isErrorMarker()) {
          // todo myData.myErrorInterner.intern(message)
          val error = createErrorNode(item, curNode, nodeData, i)
          val curToken = item.getStartTokenIndex()
          if (curToken != lastErrorIndex) { // adding only the first (deepest) error from the same lexeme offset
            lastErrorIndex = curToken
            curNode.addChild(error)
          }
        }
        else {
          val marker = createMarker(item, curNode, nodeData, i)
          curNode.addChild(marker)
          nodes.addLast(curNode to curProduction)
          curNode = marker
          curProduction = item
          curDepth++
          if (curDepth > maxDepth) maxDepth = curDepth
        }
      }

      i++
    }

    val hasCollapsedChameleons = productionResult.productionMarkers.collapsedMarkerSize > 0
    if (hasCollapsedChameleons) {
      markCollapsedNodes(productionResult, nodeData)
    }

    assertMarkersBalanced(curNode === rootMarker, curNode)

    checkTreeDepth(maxDepth, rootMarker.tokenType is IFileElementType, hasCollapsedChameleons)

    return rootMarker
  }

  private fun assertAllElementsConverted(
    lexTypes: Array<IElementType?>,
    originalLexTypes: Array<SyntaxElementType>,
  ) {
    for (i in 0..<lexTypes.size) {
      if (lexTypes[i] == null) {
        throw IllegalStateException("IElementType for token ${originalLexTypes[i]} is missing. TokenConverter = $tokenConverter")
      }
    }
  }

  private fun markCollapsedNodes(
    productionResult: ProductionResult,
    nodeData: NodeData,
  ) {
    val collapsedMarkers = productionResult.productionMarkers.collapsedMarkers
    for (index in collapsedMarkers) {
      nodeData.optionalData.markCollapsed(index)
    }
  }

  private fun assertMarkersBalanced(condition: Boolean, marker: NodeBase?) {
    if (condition) return

    reportUnbalancedMarkers(marker)
  }

  private fun reportUnbalancedMarkers(marker: NodeBase?) {
    val tokenSequence = builder.tokens
    val lexemeSize = tokenSequence.tokenCount
    val index = if (marker != null) marker.startIndex + 1 else lexemeSize

    val context = if (index < lexemeSize)
      originalText.subSequence(max(0, tokenSequence.getTokenStart(index) - 1000), tokenSequence.getTokenStart(index))
    else
      "<none>"

    val language = if (this.file != null) this.file!!.getLanguage().toString() + ", " else ""
    LOG.error("$UNBALANCED_MESSAGE\nlanguage: $language\ncontext: '$context'\nmarker id: ${marker?.toString() ?: "n/a"}")
  }

  private fun checkTreeDepth(maxDepth: Int, isFileRoot: Boolean, hasCollapsedChameleons: Boolean) {
    val file = file?.originalFile ?: return
    val flag = file.getUserData(BlockSupport.TREE_DEPTH_LIMIT_EXCEEDED)
    if (maxDepth > BlockSupport.INCREMENTAL_REPARSE_DEPTH_LIMIT) {
      if (flag != true) {
        file.putUserData(BlockSupport.TREE_DEPTH_LIMIT_EXCEEDED, true)
      }
    }
    else if (isFileRoot && flag != null && !hasCollapsedChameleons) {
      file.putUserData(BlockSupport.TREE_DEPTH_LIMIT_EXCEEDED, null)
    }
  }

  companion object {
    // todo introduce proper API?
    @JvmStatic
    fun getErrorMessage(node: LighterASTNode): @NlsContexts.DetailedDescription String? {
      return if (node is com.intellij.lang.SyntaxTreeBuilder.Production) node.errorMessage else null
    }
  }
}

internal fun extractCachedLexemes(parentCachingNode: LighterLazyParseableNode): TokenList? {
  if (parentCachingNode !is LazyParseableToken || !shouldReuseCollapsedTokens(parentCachingNode.tokenType)) {
    return null
  }
  return parentCachingNode.parsedTokenSequence
}

internal fun extractCachedLexemes(parentCachingNode: ASTNode): TokenList? {
  if (parentCachingNode !is LazyParseableElement) {
    return null
  }

  val parentElement = parentCachingNode
  parentElement.putUserData(LAZY_PARSEABLE_TOKENS, null)
  return parentElement.getUserData(LAZY_PARSEABLE_TOKENS)
}

internal fun shouldReuseCollapsedTokens(collapsed: IElementType?): Boolean {
  return collapsed is ILazyParseableElementTypeBase && collapsed.reuseCollapsedTokens()
}

internal val LAZY_PARSEABLE_TOKENS = Key.create<TokenList>("LAZY_PARSEABLE_TOKENS")

internal const val UNBALANCED_MESSAGE: @NonNls String = "Unbalanced tree. Most probably caused by unbalanced markers. " +
                                                        "Try calling setDebugMode(true) against PsiBuilder passed to identify exact location of the problem"

private val LOG: Logger = Logger.getInstance(PsiSyntaxBuilderImpl::class.java)

private val syntaxTreeBuilderLogger: com.intellij.platform.syntax.Logger = logger<SyntaxTreeBuilder>().asSyntaxLogger()
