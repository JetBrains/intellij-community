// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Experimental

package com.intellij.platform.syntax.tree

import com.intellij.platform.syntax.impl.builder.CompositeMarker
import com.intellij.platform.syntax.parser.ProductionMarkerList
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.prepareProduction
import org.jetbrains.annotations.ApiStatus

fun SyntaxTreeBuilder.toAstMarkers(): ASTMarkers {
  val productionResult = prepareProduction(this)
  val productions = productionResult.productionMarkers
  val astMarkersResult = ASTMarkersImpl()

  if (productions.size == 0)
    return astMarkersResult

  var lastErrorLexemeIndex = -1
  val nodeProductionIndices = IntStack()
  val astTreeIndices = IntStack()

  var isInsideChameleon = false
  var markersInsideChameleonCount = 0

  for (i in 0 until productions.size) {
    val item = productions.getMarker(i)
    val isEndMarker = productions.isDoneMarker(i)
    val isErrorMarker = item.isErrorMarker()

    when {
      isEndMarker -> {
        if (isInsideChameleon) {
          if (markersInsideChameleonCount != 0) {
            markersInsideChameleonCount -= 1
            continue
          }
          else {
            isInsideChameleon = false
          }
        }
        val nodeProductionStartIndex = nodeProductionIndices.pop()
        val astIndexStart = astTreeIndices.pop()
        val astIndexEnd = astMarkersResult.pushBack()

        val prevLexemeIndex = if (astIndexStart > 0)
          productionResult.productionMarkers.getLexemeIndexAt(nodeProductionStartIndex - 1)
        else 0

        val lexemeStartIndex = productionResult.productionMarkers.getLexemeIndexAt(nodeProductionStartIndex)
        val lexemeEndIndex = productionResult.productionMarkers.getLexemeIndexAt(i)

        astMarkersResult.apply {
          setMarker(
            astIndexEnd,
            (item as CompositeMarker).markerId,
            MarkerKind.End,
            collapsed = item.isCollapsed(),
            null,
            item.getNodeType()
          )

          setLexemeInfo(astIndexEnd, lexemeEndIndex - lexemeStartIndex, lexemeStartIndex - prevLexemeIndex)
          setLexemeInfo(astIndexStart, lexemeEndIndex - lexemeStartIndex, lexemeStartIndex - prevLexemeIndex)

          setMarkersCount(astIndexEnd, astIndexEnd - astIndexStart)
          setMarkersCount(astIndexStart, astIndexEnd - astIndexStart)
        }
      }

      isErrorMarker -> {
        val startLexemeIndex = item.getStartTokenIndex()
        if (startLexemeIndex == lastErrorLexemeIndex) continue
        val prevLexemeIndex = productionResult.productionMarkers.getLexemeIndexAt(i - 1)
        val startLexeme = item.getStartTokenIndex()
        val endLexeme = item.getEndTokenIndex()
        lastErrorLexemeIndex = startLexemeIndex
        val index = astMarkersResult.pushBack()
        astMarkersResult.setMarker(
          index,
          i,
          MarkerKind.Error,
          collapsed = false,
          item.getErrorMessage(),
          item.getNodeType()
        )
        astMarkersResult.setLexemeInfo(index, endLexeme - startLexeme, startLexeme - prevLexemeIndex)
      }

      else -> {
        // start marker
        if (isInsideChameleon) {
          markersInsideChameleonCount += 1
          continue
        }

        val astTreeIndex = astMarkersResult.pushBack()
        val markerId = (item as CompositeMarker).markerId
        astMarkersResult.setMarker(
          astTreeIndex,
          markerId,
          MarkerKind.Start,
          collapsed = item.isCollapsed(),
          null,
          item.getNodeType()
        )
        if (item.getNodeType().isLazyParseable() && item.isCollapsed()) {
          astMarkersResult.setChameleon(item.startIndex, newChameleonRef())
          isInsideChameleon = true
        }
        astTreeIndices.push(astTreeIndex)
        nodeProductionIndices.push(i)
      }
    }
  }
  for (i in 0..<tokens.tokenCount) {
    if (tokens.getTokenType(i)?.isLazyParseable() == true)
      astMarkersResult.setChameleon(i, newChameleonRef())
  }
  return astMarkersResult
}

private class IntStack(initialCapacity: Int = 5) {
  private var data = IntArray(initialCapacity)
  var size = 0
    private set

  fun push(t: Int) {
    if (size >= data.size) {
      data = realloc(data, data.size * 3 / 2)
    }
    data[size] = t
    size++
  }

  fun peek(): Int = if (size == 0) error("Stack is empty")
  else data[size - 1]

  fun pop(): Int = peek().also { size-- }

  operator fun get(index: Int): Int = if (index !in 0 until size) {
    throw IndexOutOfBoundsException("Index out of bounds: $index")
  }
  else data[index]

  override fun toString(): String = data.copyOf(size).contentToString()
}

private fun realloc(array: IntArray, newSize: Int): IntArray = when (newSize) {
  0 -> EMPTY_INT_ARRAY
  array.size -> array
  else -> array.copyOf(newSize)
}

private val EMPTY_INT_ARRAY = IntArray(0)

private fun ProductionMarkerList.getLexemeIndexAt(productionIndex: Int): Int {
  val marker = getMarker(productionIndex)
  val isDone = isDoneMarker(productionIndex)
  return if (isDone) marker.getEndTokenIndex() else marker.getStartTokenIndex()
}
