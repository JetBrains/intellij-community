// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.tree

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.util.fastutil.ints.Int2IntOpenHashMap
import com.intellij.util.fastutil.ints.Int2ObjectOpenHashMap
import com.intellij.util.fastutil.ints.IntArrayList
import com.intellij.util.fastutil.ints.forEach

internal class ASTMarkersImpl private constructor(
  private val packer: Packer,
  private val elementTypes: ArrayList<SyntaxElementType>,
  private val errorMessages: Int2ObjectOpenHashMap<String>,
  private val chameleonsMap: Int2ObjectOpenHashMap<ChameleonRef>, // lexemeIndex -> chameleon
  private var nextId: Int,
) : ASTMarkers {

  constructor() : this(
    packer = Packer(),
    elementTypes = ArrayList(DEFAULT_CAPACITY),
    errorMessages = Int2ObjectOpenHashMap(),
    chameleonsMap = Int2ObjectOpenHashMap(),
    nextId = 0
  )

  fun copy(): ASTMarkersImpl = ASTMarkersImpl(
    packer = packer.copy(),
    elementTypes = ArrayList(elementTypes),
    errorMessages = Int2ObjectOpenHashMap(errorMessages),
    chameleonsMap = Int2ObjectOpenHashMap(chameleonsMap),
    nextId = nextId
  )

  override val size: Int get() = packer.size

  override fun kind(i: Int): MarkerKind {
    return packer.kind(i)
  }

  override fun errorMessage(i: Int): String? {
    return if (hasError(i)) {
      errorMessages[id(i)]
    }
    else {
      null
    }
  }

  fun hasError(i: Int): Boolean {
    return packer.hasErrors(i)
  }

  fun id(i: Int): Int {
    return packer.id(i)
  }

  override fun lexemeCount(i: Int): Int {
    return packer.lexemeInfo(i).count
  }

  override fun lexemeRelOffset(i: Int): Int {
    return packer.lexemeInfo(i).relOffset
  }


  override fun collapsed(i: Int): Boolean {
    return packer.collapsed(i)
  }

  override fun markersCount(i: Int): Int {
    return packer.markersCount(i)
  }

  override fun elementType(i: Int): SyntaxElementType {
    return elementTypes[i]
  }

  override fun chameleonAt(lexemeIndex: Int): ChameleonRef {
    return chameleonsMap[lexemeIndex]!!
  }

  override fun chameleons(): List<Pair<Int, ChameleonRef>> {
    return chameleonsMap.entries.asSequence().map { (key, value) -> key to value }.toList()
  }

  private fun substituteImpl(astMarkers: ASTMarkers, i: Int, lexemeIndex: Int) {
    check(astMarkers is ASTMarkersImpl) { "unexpected class: ${astMarkers::class}" }
    check(kind(i) == MarkerKind.Start)
    val start = i
    val end = i + markersCount(i)
    val relOffset = lexemeRelOffset(i)
    val oldId2newId = Int2IntOpenHashMap()
    copyNewChameleons(lexemeIndex, astMarkers)
    removeMarkersFromMaps(start, end)
    val newNextId = computeIdsForNewMarkers(astMarkers, oldId2newId)

    copyNewAstMarkers(start, end, astMarkers)

    // after several replace operations some ids could be missed.
    // If we are in the short mode, let's renumber our ids
    renumberIfNeeded(newNextId, start, start + astMarkers.size)

    // now we need to replace their ids with our ids
    renumberNewMarkers(start, oldId2newId, astMarkers)

    // fix offset for the root
    val count = lexemeCount(i)
    setLexemeInfo(i, count, relOffset)
    setLexemeInfo(i + astMarkers.size - 1, count, relOffset)
    nextId += newNextId
  }

  private fun copyNewChameleons(startLexeme: Int, astMarkers: ASTMarkersImpl) {
    chameleonsMap.keys.forEach {
      if (startLexeme <= it && it < startLexeme + astMarkers.lexemeCount(0)) {
        chameleonsMap.remove(it)
      }
    }
    astMarkers.chameleonsMap.entries.forEach { (k, v) -> chameleonsMap[k + startLexeme] = v }
  }

  private fun renumberNewMarkers(
    start: Int,
    oldId2newId: Int2IntOpenHashMap,
    astMarkers: ASTMarkersImpl,
  ) {
    val offset = nextId
    (start until start + astMarkers.size).forEach { index ->
      val oldId = id(index)
      val newId = oldId2newId[oldId] + offset
      if (hasError(index)) {
        errorMessages[newId] = astMarkers.errorMessages[oldId]!!
      }
      packer.setId(index, newId)
    }
  }

  private fun computeIdsForNewMarkers(
    astMarkers: ASTMarkersImpl,
    oldId2newId: Int2IntOpenHashMap,
  ): Int {
    var nextNewId = 0
    (0 until astMarkers.size).forEach { index ->
      oldId2newId.computeIfAbsent(astMarkers.id(index)) { nextNewId++ }
    }
    return nextNewId
  }

  private fun renumberIfNeeded(nextNewId: Int, insertedStart: Int, insertedEnd: Int) {
    if (packer.shortMode && nextId + nextNewId > Char.MAX_VALUE.code) {
      nextId = 0
      renumber(insertedStart, insertedEnd)
    }
  }

  private fun copyNewAstMarkers(
    start: Int,
    end: Int,
    astMarkers: ASTMarkersImpl,
  ) {
    /*
    elementTypes.removeElements(start, end + 1)
    elementTypes.addElements(start, astMarkers.elementTypes.toArray(IElementType.EMPTY_ARRAY))
    packer.replace(start, end + 1, astMarkers.packer)
     */
    elementTypes.subList(start, end + 1).clear()
    elementTypes.addAll(start, astMarkers.elementTypes)
    packer.replace(start, end + 1, astMarkers.packer)
  }

  private fun removeMarkersFromMaps(start: Int, end: Int) {
    (start..end).forEach { index ->
      val currentId = id(index)
      if (hasError(index)) {
        errorMessages.remove(currentId)
      }
    }
  }

  private fun renumber(insertedStart: Int, insertedEnd: Int) {
    val renumberMap = Int2IntOpenHashMap()
    (0 until size).forEach { index ->
      // skip new elements
      if (index in insertedStart until insertedEnd) return@forEach
      val newId = renumberMap.computeIfAbsent(id(index)) { oldId ->
        val currentId = nextId++
        if (hasError(index)) {
          errorMessages[currentId] = errorMessages.remove(oldId)!!
        }
        currentId
      }
      packer.setId(index, newId)
    }
  }

  fun setChameleon(lexemeIndex: Int, reference: ChameleonRef) {
    chameleonsMap[lexemeIndex] = reference
  }

  fun setMarkersCount(i: Int, descCount: Int) {
    packer.setMarkersCount(i, descCount)
  }

  fun setLexemeInfo(i: Int, lexemeCount: Int, relOffset: Int) {
    packer.setLexemeInfo(i, lexemeCount, relOffset)
  }

  fun pushBack(): Int {
    val i = size
    packer.pushBack()
    elementTypes.add(i, SyntaxTokenTypes.ERROR_ELEMENT)
    return i
  }

  fun setMarker(
    index: Int,
    id: Int,
    kind: MarkerKind,
    collapsed: Boolean,
    errorMessage: String?,
    elementType: SyntaxElementType?,
  ) {
    if (kind(index) != MarkerKind.Undone) {
      throw AssertionError()
    }

    if (id + 1 > nextId) {
      nextId = id + 1
    }

    packer.setInitialInfo(index, id, kind, collapsed, errorMessage != null)

    if (errorMessage != null) {
      this.errorMessages.put(id, errorMessage)
    }

    if (elementType != null) {
      this.elementTypes[index] = elementType
    }
  }

  override fun toString(): String = buildString {
    var depth = 0
    elementTypes.forEachIndexed { index, type ->
      if (kind(index) == MarkerKind.End) {
        depth--
      }
      append("${"  ".repeat(depth)}$type ${kind(index)} ")
      append("e=${hasError(index)} ")
      append("c=${collapsed(index)} ")
      append("lo=${lexemeRelOffset(index)} ")
      append("lc${lexemeCount(index)} ")
      appendLine("mc=${markersCount(index)}")
      if (kind(index) == MarkerKind.Start) {
        depth++
      }

    }
    appendLine("{")
    for ((t, u) in chameleonsMap.entries) {
      appendLine("$t -> $u")
    }
    appendLine("}")
  }

  override fun mutate(mutator: ASTMarkers.MutableContext.() -> Unit): ASTMarkersImpl =
    MutableContextImpl().also(mutator).ast

  private data class LexemeInfo(val relOffset: Int, val count: Int)
  private class Packer {
    // short mode: (kind(2) + collapsed(1) + hasError(1) + markersCount(12) + id(16), lexemeRelOffset(16) + lexemeCount(16) = 2
    // long mode: kind(2) + collapsed(1) + hasError(1) + markersCount(28), id(32), lexemeRelOffset(32), lexemeCount(32) = 4 ints
    private val ints: IntArrayList

    constructor() {
      ints = IntArrayList(DEFAULT_CAPACITY)
    }

    private constructor(origin: Packer) {
      longMode = origin.longMode
      ints = origin.ints.clone()
    }

    var longMode = false
      private set

    val shortMode: Boolean
      get() = !longMode

    private fun index(i: Int): Int = if (longMode) i * 4 else i * 2

    fun kind(i: Int): MarkerKind = MarkerKind.entries[(ints[index(i)] and KIND_MASK)]

    fun lexemeInfo(i: Int): LexemeInfo {
      val index = index(i)
      return if (longMode) {
        LexemeInfo(ints[index + 2], ints[index + 3])
      }
      else {
        val packed = ints[index + 1]
        LexemeInfo(packed and MAX_SHORT_LEXEME_VALUE, packed ushr 16)
      }
    }

    fun collapsed(i: Int): Boolean {
      return ints[index(i)] and 4 == 4
    }

    fun hasErrors(i: Int): Boolean {
      return ints[index(i)] and 8 == 8
    }

    fun id(i: Int): Int {
      val index = index(i)
      return if (longMode) ints[index + 1] else ints[index] ushr 16
    }

    fun markersCount(i: Int): Int =
      (ints[index(i)] ushr 4) and (if (longMode) MAX_LONG_MARKERS_COUNT else MAX_SHORT_MARKERS_COUNT)


    val size: Int
      get() = ints.size / (if (longMode) 4 else 2)

    fun setLexemeInfo(i: Int, count: Int, relOffset: Int) {
      if (shortMode && (relOffset > MAX_SHORT_LEXEME_VALUE || count > MAX_SHORT_LEXEME_VALUE)) grow()
      val index = index(i)
      if (longMode) {
        ints[index + 2] = relOffset
        ints[index + 3] = count
      }
      else {
        ints[index + 1] = relOffset or (count shl 16)
      }
    }

    fun setMarkersCount(i: Int, count: Int) {
      if (shortMode && count > MAX_SHORT_MARKERS_COUNT) grow()
      check(count <= MAX_LONG_MARKERS_COUNT) { "markers count $count is bigger than $MAX_LONG_MARKERS_COUNT" }
      val index = index(i)
      ints[index] = (ints[index] and (MAX_SHORT_MARKERS_COUNT shl 4).inv()) or (count shl 4)
    }

    fun setInitialInfo(i: Int, id: Int, kind: MarkerKind, collapsed: Boolean, hasErrors: Boolean) {
      val collapsedInt = if (collapsed) 4 else 0
      val hasErrorInt = if (hasErrors) 8 else 0
      if (shortMode && id > MAX_SHORT_ID_VALUE) grow()
      val index = index(i)
      ints[index] = kind.ordinal + collapsedInt + hasErrorInt
      setId(i, id)
    }

    fun setId(i: Int, id: Int) {
      val index = index(i)
      if (longMode) {
        ints[index + 1] = id
      }
      else {
        ints[index] = (ints[index] and Char.MAX_VALUE.code) or (id shl 16)
      }
    }

    fun replace(start: Int, end: Int, packer: Packer) {
      if (longMode && !packer.longMode) packer.grow()
      if (!longMode && packer.longMode) grow()
      ints.removeElements(index(start), index(end))
      ints.addElements(index(start), packer.ints.elements(), 0, packer.ints.size)
    }

    private fun grow() {
      check(shortMode) { "Already in long mode" }
      val end = size - 1
      repeat(ints.size) { ints.add(0) }
      longMode = true
      (end downTo 0).forEach { i ->
        val firstInt = ints[i * 2]
        val secondInt = ints[i * 2 + 1]
        setInitialInfo(
          i,
          firstInt ushr 16,
          MarkerKind.entries[(firstInt and 3)],
          (firstInt and 4) == 4,
          (firstInt and 8) == 8
        )
        setMarkersCount(i, (firstInt ushr 4) and MAX_SHORT_MARKERS_COUNT)
        setLexemeInfo(i, secondInt ushr 16, secondInt and MAX_SHORT_LEXEME_VALUE)
      }
    }

    fun pushBack() {
      repeat(if (longMode) 4 else 2) { ints.add(0) }
    }

    fun copy(): Packer = Packer(this)

    companion object {
      private const val KIND_MASK = 3
      private const val MAX_SHORT_ID_VALUE = Char.MAX_VALUE.code
      private const val MAX_SHORT_LEXEME_VALUE = Char.MAX_VALUE.code
      private const val MAX_SHORT_MARKERS_COUNT = Char.MAX_VALUE.code ushr 4
      private const val MAX_LONG_MARKERS_COUNT = Int.MAX_VALUE ushr 3
    }
  }

  private inner class MutableContextImpl : ASTMarkers.MutableContext {
    var astChanged = false

    var ast: ASTMarkersImpl = this@ASTMarkersImpl

    fun copyIfNeeded() {
      if (!astChanged) {
        ast = ast.copy()
        astChanged = true
      }
    }

    override fun substitute(i: Int, lexemeIndex: Int, astMarkers: ASTMarkers) {
      copyIfNeeded()
      ast.substituteImpl(astMarkers, i, lexemeIndex)
    }

    override fun changeChameleons(pairs: List<Pair<Int, ChameleonRef>>) {
      ast = ast.withChameleons(pairs)
    }

    override fun changeLexCount(startMarker: Int, endMarker: Int, lexCount: Int) {
      copyIfNeeded()
      val relOffset = ast.lexemeRelOffset(startMarker)
      ast.setLexemeInfo(startMarker, lexCount, relOffset)
      ast.setLexemeInfo(endMarker, lexCount, relOffset)
    }

    override fun changeMarkerCount(startMarker: Int, endMarker: Int, markerCount: Int) {
      copyIfNeeded()
      ast.setMarkersCount(startMarker, markerCount)
      ast.setMarkersCount(endMarker, markerCount)
    }
  }

  private fun withChameleons(pairs: List<Pair<Int, ChameleonRef>>): ASTMarkersImpl =
    ASTMarkersImpl(
      packer = packer,
      elementTypes = elementTypes,
      errorMessages = errorMessages,
      chameleonsMap = Int2ObjectOpenHashMap<ChameleonRef>().apply {
        pairs.forEach { (i, ref) ->
          put(i, ref)
        }
      },
      nextId = nextId
    )
}

private const val DEFAULT_CAPACITY = 256
