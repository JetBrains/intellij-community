// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Experimental

package com.intellij.platform.syntax.tree

import com.intellij.platform.syntax.impl.builder.CompositeMarker
import com.intellij.platform.syntax.parser.ProductionMarkerList
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.prepareProduction
import com.intellij.util.fastutil.ints.IntArrayList
import com.intellij.util.fastutil.ints.pop
import org.jetbrains.annotations.ApiStatus

fun SyntaxTreeBuilder.toAstMarkers(): ASTMarkers {
  val productionResult = prepareProduction(this)
  val productions = productionResult.productionMarkers
  return AstMarkerBuilder(this, productions).build()
}

private class AstMarkerBuilder(
  private val builder: SyntaxTreeBuilder,
  private val productions: ProductionMarkerList
) {
  private val astMarkersResult = ASTMarkersImpl()

  fun build(): ASTMarkersImpl {
    if (productions.size == 0) {
      return astMarkersResult
    }

    processTree()
    processLeafChameleons()

    return astMarkersResult
  }

  private val nodeProductionIndices = IntArrayList()
  private val astTreeIndices = IntArrayList()

  private var lastErrorLexemeIndex = -1

  private var isInsideChameleon = false
  private var markersInsideChameleonCount = 0

  private fun processTree() {
    for (i in 0 until productions.size) {
      val item = productions.getMarker(i)

      when {
        productions.isDoneMarker(i) -> {
          processDoneMarker(item, i)
        }

        item.isErrorMarker() -> {
          processErrorMarker(item, i)
        }

        else -> {
          // start marker
          processStartMarker(item, i)
        }
      }
    }
  }

  private fun processStartMarker(item: SyntaxTreeBuilder.Production, i: Int) {
    if (isInsideChameleon) {
      markersInsideChameleonCount += 1
      return
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
    astTreeIndices.add(astTreeIndex)
    nodeProductionIndices.add(i)
  }

  private fun processErrorMarker(item: SyntaxTreeBuilder.Production, i: Int) {
    val startLexemeIndex = item.getStartTokenIndex()
    if (startLexemeIndex == lastErrorLexemeIndex) return
    val prevLexemeIndex = productions.getLexemeIndexAt(i - 1)
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

  private fun processDoneMarker(item: SyntaxTreeBuilder.Production, i: Int) {
    if (isInsideChameleon) {
      if (markersInsideChameleonCount != 0) {
        markersInsideChameleonCount -= 1
        return
      }
      else {
        isInsideChameleon = false
      }
    }
    val nodeProductionStartIndex = nodeProductionIndices.pop()
    val astIndexStart = astTreeIndices.pop()
    val astIndexEnd = astMarkersResult.pushBack()

    val prevLexemeIndex = if (astIndexStart > 0)
      productions.getLexemeIndexAt(nodeProductionStartIndex - 1)
    else 0

    val lexemeStartIndex = productions.getLexemeIndexAt(nodeProductionStartIndex)
    val lexemeEndIndex = productions.getLexemeIndexAt(i)

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

  private fun processLeafChameleons() {
    for (i in 0..<builder.tokens.tokenCount) {
      val type = builder.tokens.getTokenType(i) ?: continue
      if (type.isLazyParseable()) {
        astMarkersResult.setChameleon(i, newChameleonRef())
      }
    }
  }
}

private fun ProductionMarkerList.getLexemeIndexAt(productionIndex: Int): Int {
  val marker = getMarker(productionIndex)
  val isDone = isDoneMarker(productionIndex)
  return if (isDone) marker.getEndTokenIndex() else marker.getStartTokenIndex()
}
