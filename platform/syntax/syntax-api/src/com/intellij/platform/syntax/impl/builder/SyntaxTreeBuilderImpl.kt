// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import com.intellij.platform.syntax.CancellationProvider
import com.intellij.platform.syntax.Logger
import com.intellij.platform.syntax.Logger.Attachment
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.platform.syntax.lexer.TokenListImpl
import com.intellij.platform.syntax.parser.*
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder.Production
import com.intellij.util.fastutil.ints.isEmpty
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import kotlin.math.abs
import kotlin.math.max

internal class SyntaxTreeBuilderImpl(
  override val text: CharSequence,
  val myWhitespaces: SyntaxElementTypeSet,
  private var myComments: SyntaxElementTypeSet,
  val startOffset: Int,
  private var myWhitespaceSkippedCallback: WhitespaceSkippedCallback?,
  override val tokens: TokenList,
  private var myDebugMode: Boolean,
  val language: String?,
  val logger: Logger,
  val cancellationProvider: CancellationProvider?,
  private val whitespaceOrCommentBindingPolicy: WhitespaceOrCommentBindingPolicy,
  private val opaquePolicy: OpaqueElementPolicy,
) : SyntaxTreeBuilder {

  private val lexStartIndex = (tokens as? TokenListImpl)?.startIndex ?: 0
  private val myLexStarts: IntArray? = (tokens as? TokenListImpl)?.lexStarts
  private val myLexTypes: Array<SyntaxElementType>? = (tokens as? TokenListImpl)?.lexTypes

  private val lexemeCount: Int = tokens.tokenCount

  internal val pool: MarkerPool = MarkerPool(this)
  internal val myOptionalData: MarkerOptionalData = MarkerOptionalData()
  internal val myProduction: MarkerProduction = MarkerProduction(pool, myOptionalData, logger)

  val errorInterner = StringInterner()

  // mutable state
  private var myTokenTypeChecked = false
  private var myCurrentLexeme = 0
  private var myCachedTokenType: SyntaxElementType? = null

  private var productionResult: ProductionResult? = null

  // shame for this mutable state
  private var myRemapper: SyntaxElementTypeRemapper? = null

  init {
    DIAGNOSTICS?.registerPass(text.length, lexemeCount)
  }

  override val lastDoneMarker: SyntaxTreeBuilder.Marker?
    get() = ((myProduction.size - 1) downTo 0).firstNotNullOfOrNull { index ->
      myProduction.getDoneMarkerAt(index)
    }

  override val productions: List<Production>
    get() = object : AbstractList<Production>() {
      override fun get(index: Int): Production = myProduction.getMarkerAt(index) as Production
      override val size: Int get() = myProduction.size
    }

  internal fun precede(marker: ProductionMarker): SyntaxTreeBuilder.Marker {
    require(marker.getStartTokenIndex() >= 0) { "Preceding disposed marker" }
    if (myDebugMode) {
      myProduction.assertNoDoneMarkerAround(marker)
    }
    val pre = createMarker(marker.getStartTokenIndex())
    myProduction.addBefore(pre, marker)
    return pre
  }

  override val tokenType: SyntaxElementType?
    get() = myCachedTokenType ?: calcTokenType().also { myCachedTokenType = it }

  override fun isWhitespaceOrComment(elementType: SyntaxElementType): Boolean {
    return myWhitespaces.contains(elementType) || myComments.contains(elementType)
  }

  private fun clearCachedTokenType() {
    myCachedTokenType = null
  }

  private fun remapCurrentToken(): SyntaxElementType {
    myCachedTokenType?.let {
      return it
    }

    myRemapper?.let { remapper ->
      val source = lexType(myCurrentLexeme)
      val start = lexStart(myCurrentLexeme)
      val end = lexStart(myCurrentLexeme + 1)
      val type = remapper.remap(source, start, end, text)
      remapCurrentToken(type)
    }

    return lexType(myCurrentLexeme)
  }

  private fun calcTokenType(): SyntaxElementType? {
    if (eof()) return null

    if (myRemapper != null) {
      //remaps current token, and following, which remaps to spaces and comments
      skipWhitespace()
    }
    return lexType(myCurrentLexeme)
  }

  override fun setTokenTypeRemapper(remapper: SyntaxElementTypeRemapper?) {
    myRemapper = remapper
    myTokenTypeChecked = false
    clearCachedTokenType()
  }

  override fun setWhitespaceSkippedCallback(callback: WhitespaceSkippedCallback?) {
    myWhitespaceSkippedCallback = callback
  }

  override fun enforceCommentTokens(tokens: SyntaxElementTypeSet) {
    myComments = tokens
  }

  override fun remapCurrentToken(type: SyntaxElementType) {
    remap(myCurrentLexeme, type)
    clearCachedTokenType()
  }

  override fun lookAhead(steps: Int): SyntaxElementType? {
    var steps = steps
    var cur = shiftOverWhitespaceForward(myCurrentLexeme)

    while (steps > 0) {
      cur = shiftOverWhitespaceForward(cur + 1)
      steps--
    }

    return if (cur < lexemeCount) lexType(cur) else null
  }

  private fun shiftOverWhitespaceForward(lexIndex: Int): Int {
    var lexIndex = lexIndex
    while (lexIndex < lexemeCount && isWhitespaceOrComment(lexType(lexIndex))) {
      lexIndex++
    }
    return lexIndex
  }

  override fun rawLookup(steps: Int): SyntaxElementType? {
    val cur = myCurrentLexeme + steps
    return if (cur < lexemeCount && cur >= 0) lexType(cur) else null
  }

  override fun rawTokenTypeStart(steps: Int): Int {
    val cur = myCurrentLexeme + steps
    if (cur < 0) return -1
    if (cur >= lexemeCount) return text.length
    return lexStart(cur)
  }

  override fun rawTokenIndex(): Int = myCurrentLexeme

  override fun rawAdvanceLexer(steps: Int) {
    cancellationProvider?.checkCancelled()
    require(steps >= 0) {
      "Steps must be a positive integer - lexer can only be advanced. " +
      "Use Marker.rollbackTo if you want to rollback PSI building."
    }
    if (steps == 0) return
    // Be permissive as advanceLexer() and don't throw error if advancing beyond eof state
    myCurrentLexeme += steps
    if (myCurrentLexeme > lexemeCount || myCurrentLexeme < 0 /* int overflow */) {
      myCurrentLexeme = lexemeCount
    }
    myTokenTypeChecked = false
    clearCachedTokenType()
  }

  override fun advanceLexer() {
    if ((myCurrentLexeme and 0xff) == 0) {
      cancellationProvider?.checkCancelled()
    }

    if (eof()) return

    myTokenTypeChecked = false
    myCurrentLexeme++
    clearCachedTokenType()
  }

  fun lexType(index: Int): SyntaxElementType {
    return if (myLexTypes != null) myLexTypes[index + lexStartIndex] else tokens.getTokenType(index)!!
  }

  fun lexStart(index: Int): Int {
    return if (myLexStarts != null) myLexStarts[index + lexStartIndex] else tokens.getTokenStart(index)
  }

  fun remap(index: Int, value: SyntaxElementType) {
    if (myLexTypes != null) myLexTypes[index + lexStartIndex] = value else tokens.remap(index, value)
  }

  private fun skipWhitespace() {
    while (myCurrentLexeme < lexemeCount && isWhitespaceOrComment(remapCurrentToken())) {
      val type = lexType(myCurrentLexeme)
      val start = lexStart(myCurrentLexeme)
      val end = if (myCurrentLexeme + 1 < lexemeCount) lexStart(myCurrentLexeme + 1) else text.length
      onSkip(type, start, end)
      myCurrentLexeme++
      clearCachedTokenType()
    }
  }

  private fun onSkip(type: SyntaxElementType, start: Int, end: Int) {
    myWhitespaceSkippedCallback?.onSkip(type, start, end)
  }

  override val currentOffset: Int
    get() {
      if (eof()) return text.length
      return lexStart(myCurrentLexeme)
    }

  override val tokenText: String?
    get() {
      if (eof()) return null

      tokenType?.let { type ->
        opaquePolicy.getTextOfOpaqueElement(type)?.let {
          return it
        }
      }

      return text.subSequence(lexStart(myCurrentLexeme), lexStart(myCurrentLexeme + 1)).toString()
    }

  override fun mark(): SyntaxTreeBuilder.Marker {
    if (!myProduction.isEmpty()) {
      skipWhitespace()
    }

    val marker = createMarker(myCurrentLexeme)
    myProduction.addMarker(marker)
    return marker as SyntaxTreeBuilder.Marker
  }

  private fun createMarker(lexemeIndex: Int): CompositeMarker {
    val marker = pool.allocateCompositeMarker()
    marker.startIndex = lexemeIndex
    if (myDebugMode) {
      myOptionalData.notifyAllocated(marker.markerId)
    }
    return marker
  }

  override fun eof(): Boolean {
    if (!myTokenTypeChecked) {
      myTokenTypeChecked = true
      skipWhitespace()
    }
    return myCurrentLexeme >= lexemeCount
  }

  override fun setDebugMode(dbgMode: Boolean) {
    this.myDebugMode = dbgMode
  }

  internal fun rollbackTo(marker: CompositeMarker) {
    require(marker.getStartTokenIndex() >= 0) { "The marker is already disposed" }
    if (myDebugMode) {
      myProduction.assertNoDoneMarkerAround(marker)
    }
    DIAGNOSTICS?.registerRollback(myCurrentLexeme - marker.getStartTokenIndex())
    myCurrentLexeme = marker.getStartTokenIndex()
    myTokenTypeChecked = true
    myProduction.rollbackTo(marker)
    clearCachedTokenType()
  }

  /**
   * @return true if there are error elements created and not dropped after marker was created
   */
  override fun hasErrorsAfter(marker: SyntaxTreeBuilder.Marker): Boolean {
    return myProduction.hasErrorsAfter(marker as CompositeMarker)
  }

  internal fun processDone(marker: CompositeMarker, errorMessage: @Nls String?, before: CompositeMarker?) {
    doValidityChecks(marker, before)

    if (errorMessage != null) {
      myOptionalData.setErrorMessage(marker.markerId, errorMessage)
    }

    val doneLexeme = before?.getStartTokenIndex() ?: myCurrentLexeme
    if (whitespaceOrCommentBindingPolicy.isLeftBound(marker.getNodeType()) && isEmpty(marker.getStartTokenIndex(), doneLexeme)) {
      marker.setCustomEdgeTokenBinders(WhitespacesBinders.defaultRightBinder(), null)
    }
    marker.endIndex = doneLexeme
    myProduction.addDone(marker, before)
  }

  private fun isEmpty(startIdx: Int, endIdx: Int): Boolean {
    return (startIdx..<endIdx).all { i ->
      isWhitespaceOrComment(lexType(i))
    }
  }

  private fun doValidityChecks(marker: CompositeMarker, before: CompositeMarker?) {
    if (marker.isDone) {
      logger.error("Marker already done.")
    }

    if (myDebugMode) {
      myProduction.doHeavyChecksOnMarkerDone(marker, before)
    }
  }

  override fun error(messageText: @Nls String) {
    val lastMarker = myProduction.getStartMarkerAt(myProduction.size - 1)
    if (lastMarker is ErrorMarker && lastMarker.getStartTokenIndex() == myCurrentLexeme) {
      return
    }
    val marker = pool.allocateErrorNode()
    marker.setErrorMessage(messageText)
    marker.startIndex = myCurrentLexeme
    myProduction.addMarker(marker)
  }

  fun prepareProduction(): ProductionResult {
    if (myProduction.isEmpty()) {
      logger.error("Parser produced no markers. Text:\n$text")
    }
    // build tree only once to avoid threading issues in read-only PSI
    productionResult?.let { return it }

    balanceWhiteSpaces()

    val result = ProductionResultImpl()

    if (myCurrentLexeme < lexemeCount) {
      val missed = (myCurrentLexeme until lexemeCount).map { lexType(it) }
      logger.error("Tokens $missed were not inserted into the tree. ${language ?: ""}",
                   Attachment("missedTokensFragment.txt", text.toString()))
    }

    val rootMarker = result.productionMarkers.getMarker(0)
    if (rootMarker.getEndTokenIndex() < lexemeCount) {
      val missed = arrayOfNulls<SyntaxElementType>(this.lexemeCount - rootMarker.getEndTokenIndex())
      result.copyTokenTypesToArray(missed, rootMarker.getEndTokenIndex(), 0, missed.size)
      logger.error("Tokens ${missed.contentToString()} are outside of root element \"${rootMarker.getNodeType()}\".",
                   Attachment("outsideTokensFragment.txt", text.toString()))
    }

    productionResult = result

    return result
  }

  inner class ProductionResultImpl : ProductionResult {
    override val productionMarkers: ProductionMarkerList = object : ProductionMarkerList {
      override fun getMarker(index: Int): Production {
        return myProduction.getMarkerAt(index) as Production
      }

      override fun isDoneMarker(index: Int): Boolean {
        return myProduction[index] < 0
      }

      override val size: Int
        get() = myProduction.size

      override val collapsedMarkerSize: Int
        get() = myOptionalData.collapsedMarkerSize

      override val collapsedMarkers: IntArray
        get() {
          if (collapsedMarkerSize == 0) return IntArray(0)

          // collapsed marker ids are different from production marker ids!!!
          // production marker ids are defined by [myProduction]
          // marker ids are defined by [pool]
          val markerId2productionId = IntArray(pool.size)
          for (i in 0 until myProduction.size) {
            myProduction.getStartMarkerAt(i)?.let { m ->
              markerId2productionId[m.markerId] = i
            }
          }

          // replacing marker ids with production marker ids
          val collapsedMarkers = myOptionalData.collapsedMarkerIds
          for (i in 0 until collapsedMarkers.size) {
            collapsedMarkers[i] = markerId2productionId[collapsedMarkers[i]]
          }
          return collapsedMarkers
        }
    }

    override val tokenSequence: TokenList
      get() = tokens

    override fun copyTokenStartsToArray(dest: IntArray, srcStart: Int, destStart: Int, length: Int) {
      if (myLexStarts != null) {
        myLexStarts.copyInto(dest, destStart, srcStart + lexStartIndex, srcStart + length)
      }
      else {
        for (i in 0 until length) {
          dest[destStart + i] = tokens.getTokenStart(srcStart + i)
        }
      }
    }

    override fun copyTokenTypesToArray(dest: Array<in SyntaxElementType>, srcStart: Int, destStart: Int, length: Int) {
      if (myLexTypes != null) {
        myLexTypes.copyInto(dest, destStart, srcStart + lexStartIndex, srcStart + length)
      }
      else {
        for (i in 0 until length) {
          dest[destStart + i] = tokens.getTokenType(srcStart + i)!!
        }
      }
    }
  }

  private fun balanceWhiteSpaces() {
    myTokenTypeChecked = true
    val wsTokens = RelativeTokenTypesView()
    val tokenTextGetter = RelativeTokenTextView()
    var lastIndex = 0

    val size = myProduction.size - 1
    for (i in 1 until size) {
      val id = myProduction[i]
      val starting = if (id > 0) pool.get(id) else null
      if (starting is CompositeMarker && !starting.isDone) {
        logger.error(prepareUnbalancedMarkerMessage(starting))
      }
      val done = starting == null
      val item = starting ?: pool.get(-id)

      val binder = if (item is ErrorMarker) {
        check(!done)
        WhitespacesBinders.defaultRightBinder()
      }
      else {
        myOptionalData.getBinder(item.markerId, done)
      }
      var lexemeIndex = item.getLexemeIndex(done)

      val recursive = binder.isRecursive()
      val prevProductionLexIndex: Int = if (recursive) 0
      else {
        val prevId = myProduction[i - 1]
        pool.get(abs(prevId)).getLexemeIndex(prevId < 0)
      }
      var wsStartIndex = max(lexemeIndex, lastIndex)
      while (wsStartIndex > prevProductionLexIndex && isWhitespaceOrComment(lexType(wsStartIndex - 1))) wsStartIndex--

      val wsEndIndex = shiftOverWhitespaceForward(lexemeIndex)

      if (wsStartIndex != wsEndIndex) {
        wsTokens.configure(wsStartIndex, wsEndIndex)
        tokenTextGetter.configure(wsStartIndex)
        val atEnd = wsStartIndex == 0 || wsEndIndex == lexemeCount
        lexemeIndex = wsStartIndex + binder.getEdgePosition(wsTokens, atEnd, tokenTextGetter)
        item.setLexemeIndex(lexemeIndex, done)
        if (recursive) {
          myProduction.confineMarkersToMaxLexeme(i, lexemeIndex)
        }
      }
      else if (lexemeIndex < wsStartIndex) {
        lexemeIndex = wsStartIndex
        item.setLexemeIndex(wsStartIndex, done)
      }

      lastIndex = lexemeIndex
    }
  }

  private fun prepareUnbalancedMarkerMessage(marker: ProductionMarker?): String {
    val index = if (marker != null) marker.getStartTokenIndex() + 1 else lexemeCount

    val context = if (index < lexemeCount)
      text.subSequence(max(0, (lexStart(index) - 1000)), lexStart(index))
    else
      "<none>"

    return """
      $UNBALANCED_MESSAGE
      language: ${language}
      context: '$context'
      marker id: ${marker?.markerId ?: "n/a"}
      """.trimIndent()
  }

  private inner class RelativeTokenTypesView : AbstractList<SyntaxElementType>() {
    private var myStart = 0
    override var size = 0

    fun configure(start: Int, end: Int) {
      myStart = start
      size = end - start
    }

    override fun get(index: Int): SyntaxElementType {
      return lexType(myStart + index)
    }
  }

  private inner class RelativeTokenTextView : WhitespacesAndCommentsBinder.TokenTextGetter {
    private var myStart = 0

    fun configure(start: Int) {
      myStart = start
    }

    override fun get(i: Int): CharSequence {
      return CharSequenceSubSequence(text, lexStart(myStart + i), lexStart(myStart + i + 1))
    }
  }
}

private const val UNBALANCED_MESSAGE: @NonNls String = "Unbalanced tree. Most probably caused by unbalanced markers. " +
                                                       "Try calling setDebugMode(true) against PsiBuilder passed to identify exact location of the problem"
