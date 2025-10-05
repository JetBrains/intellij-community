// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Experimental

package com.intellij.platform.syntax.tree

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.TokenList
import fleet.util.multiplatform.linkToActual
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class AstMarkersChameleon(
  val customLexemeStore: TokenList?,
  val ast: ASTMarkers,
)

@ApiStatus.Experimental
interface ChameleonRef {
  val value: AstMarkersChameleon?
  fun set(value: AstMarkersChameleon)
  fun realize(func: () -> AstMarkersChameleon): AstMarkersChameleon
}

fun newChameleonRef(): ChameleonRef = linkToActual()

@Suppress("unused")
fun newChameleonRef(chameleon: AstMarkersChameleon): ChameleonRef = linkToActual()

@ApiStatus.Experimental
interface ASTMarkers {
  val size: Int
  fun kind(i: Int): MarkerKind

  fun errorMessage(i: Int): String?

  fun lexemeCount(i: Int): Int
  fun lexemeRelOffset(i: Int): Int

  fun collapsed(i: Int): Boolean

  fun markersCount(i: Int): Int

  fun elementType(i: Int): SyntaxElementType
  fun chameleonAt(lexemeIndex: Int): ChameleonRef

  fun chameleons(): List<Pair<Int, ChameleonRef>>

  fun mutate(mutator: MutableContext.() -> Unit): ASTMarkers

  @ApiStatus.Experimental
  interface MutableContext {
    fun substitute(i: Int, lexemeIndex: Int, astMarkers: ASTMarkers)
    fun changeChameleons(pairs: List<Pair<Int, ChameleonRef>>)
    fun changeLexCount(startMarker: Int, endMarker: Int, lexCount: Int)
    fun changeMarkerCount(startMarker: Int, endMarker: Int, markerCount: Int)
  }
}

@ApiStatus.Experimental
enum class MarkerKind {
  Undone, Start, End, Error,
}

fun ASTMarkers.prevSibling(markerIndex: Int): Int = if (markerIndex == 0) {
  -1
}
else {
  val prevMarkerIndex = markerIndex - 1
  when (kind(prevMarkerIndex)) {
    MarkerKind.Start -> -1
    MarkerKind.End -> prevMarkerIndex - markersCount(prevMarkerIndex)
    MarkerKind.Error -> prevMarkerIndex
    else -> error("no else")
  }
}


fun ASTMarkers.nextSibling(markerIndex: Int): Int = when (kind(markerIndex)) {
  MarkerKind.Start -> {
    val endIndex = markerIndex + markersCount(markerIndex)
    if (endIndex == size - 1) {
      -1
    }
    else {
      val nextToEndIndex = endIndex + 1
      when (kind(nextToEndIndex)) {
        MarkerKind.Start -> nextToEndIndex
        MarkerKind.End -> -1
        MarkerKind.Error -> nextToEndIndex
        else -> error("no else")
      }
    }
  }

  MarkerKind.Error -> {
    val nextKind = kind(markerIndex + 1)
    if (nextKind == MarkerKind.End) {
      -1
    }
    else {
      markerIndex + 1
    }
  }

  MarkerKind.End -> error("should never be at the end")
  else -> error("no else")
}

fun ASTMarkers.lastChild(markerIndex: Int): Int = when (kind(markerIndex)) {
  MarkerKind.End -> error("never at end")
  MarkerKind.Error -> -1
  MarkerKind.Start -> {
    val prevToEndIndex = markerIndex + markersCount(markerIndex) - 1
    when (kind(prevToEndIndex)) {
      MarkerKind.Start -> -1
      MarkerKind.End -> {
        prevToEndIndex - markersCount(prevToEndIndex)
      }

      MarkerKind.Error -> prevToEndIndex
      else -> error("no else")
    }
  }

  else -> error("no else")
}

fun ASTMarkers.firstChild(markerIndex: Int): Int {
  require(markerIndex < size - 1) {
    "at least there is an end"
  }

  val nextMarkerIndex = markerIndex + 1
  return when (kind(nextMarkerIndex)) {
    MarkerKind.Start -> nextMarkerIndex
    MarkerKind.End -> -1
    MarkerKind.Error -> nextMarkerIndex
    else -> error("no else")
  }
}