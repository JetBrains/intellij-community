// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.tree.ASTMarkersImpl
import com.intellij.platform.syntax.tree.newChameleonRef
import com.intellij.platform.syntax.tree.MarkerKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ASTMarkersTests {
  @Test
  fun testAdd() = ASTMarkersImpl().run {
    val index = pushBack()
    setMarker(index, 1, MarkerKind.Start, false, null, null)
    setMarkersCount(index, 10)
    setLexemeInfo(index, 20, 30)

    kind(index) shouldBe MarkerKind.Start
    collapsed(index) shouldBe false
    errorMessage(index) shouldBe null
    id(index) shouldBe 1
    markersCount(index) shouldBe 10
    lexemeCount(index) shouldBe 20
    lexemeRelOffset(index) shouldBe 30
  }

  @Test
  fun testErrorMessage() = ASTMarkersImpl().run {
    val index = pushBack()
    setMarker(index, 1, MarkerKind.Start, collapsed = true, errorMessage = "foo", elementType = null)

    collapsed(index) shouldBe true
    errorMessage(index) shouldBe "foo"

    setMarkersCount(index, descCount = 10)
    setLexemeInfo(index, lexemeCount = 20, relOffset = 30)

    markersCount(index) shouldBe 10
    lexemeCount(index) shouldBe 20
    lexemeRelOffset(index) shouldBe 30
  }

  @Test
  fun testGrowMark() = ASTMarkersImpl().run {
    var index = pushBack()
    setMarker(index, 1, MarkerKind.Start, false, null, null)
    setMarkersCount(index, 10)
    setLexemeInfo(index, 20, 30)

    kind(0) shouldBe MarkerKind.Start
    collapsed(0) shouldBe false
    errorMessage(0) shouldBe null
    id(0) shouldBe 1
    markersCount(0) shouldBe 10
    lexemeCount(0) shouldBe 20
    lexemeRelOffset(0) shouldBe 30

    index = pushBack()
    setMarker(index, 2, MarkerKind.End, false, null, null)
    setMarkersCount(index, 10000)
    setLexemeInfo(index, 2000, 3000)

    kind(0) shouldBe MarkerKind.Start
    collapsed(0) shouldBe false
    errorMessage(0) shouldBe null
    id(0) shouldBe 1
    markersCount(0) shouldBe 10
    lexemeCount(0) shouldBe 20
    lexemeRelOffset(0) shouldBe 30

    kind(index) shouldBe MarkerKind.End
    collapsed(index) shouldBe false
    errorMessage(index) shouldBe null
    id(index) shouldBe 2
    markersCount(index) shouldBe 10000
    lexemeCount(index) shouldBe 2000
    lexemeRelOffset(index) shouldBe 3000
  }

  @Test
  fun testGrowLexemeCount() = ASTMarkersImpl().run {
    var index = pushBack()
    setMarker(index, 1, MarkerKind.Start, false, null, null)
    setMarkersCount(index, 10)
    setLexemeInfo(index, 20, 30)

    kind(index) shouldBe MarkerKind.Start
    collapsed(index) shouldBe false
    errorMessage(index) shouldBe null
    id(index) shouldBe 1
    markersCount(index) shouldBe 10
    lexemeCount(index) shouldBe 20
    lexemeRelOffset(index) shouldBe 30

    index = pushBack()
    setMarker(index, 2, MarkerKind.End, false, null, null)
    setMarkersCount(index, 2000)
    setLexemeInfo(index, 200000, 3000)

    kind(0) shouldBe MarkerKind.Start
    collapsed(0) shouldBe false
    errorMessage(0) shouldBe null
    id(0) shouldBe 1
    markersCount(0) shouldBe 10
    lexemeCount(0) shouldBe 20
    lexemeRelOffset(0) shouldBe 30

    kind(index) shouldBe MarkerKind.End
    collapsed(index) shouldBe false
    errorMessage(index) shouldBe null
    id(index) shouldBe 2
    markersCount(index) shouldBe 2000
    lexemeCount(index) shouldBe 200000
    lexemeRelOffset(index) shouldBe 3000
  }

  @Test
  fun testGrowLexemeRelOffset() = ASTMarkersImpl().run {
    var index = pushBack()
    setMarker(index, 1, MarkerKind.Start, false, null, null)
    setMarkersCount(index, 10)
    setLexemeInfo(index, 20, 30)

    kind(index) shouldBe MarkerKind.Start
    collapsed(index) shouldBe false
    errorMessage(index) shouldBe null
    id(index) shouldBe 1
    markersCount(index) shouldBe 10
    lexemeCount(index) shouldBe 20
    lexemeRelOffset(index) shouldBe 30

    index = pushBack()
    setMarker(index, 2, MarkerKind.End, false, null, null)
    setMarkersCount(index, 1000)
    setLexemeInfo(index, 2000, 300000)

    kind(0) shouldBe MarkerKind.Start
    collapsed(0) shouldBe false
    errorMessage(0) shouldBe null
    id(0) shouldBe 1
    markersCount(0) shouldBe 10
    lexemeCount(0) shouldBe 20
    lexemeRelOffset(0) shouldBe 30

    kind(index) shouldBe MarkerKind.End
    collapsed(index) shouldBe false
    errorMessage(index) shouldBe null
    id(index) shouldBe 2
    markersCount(index) shouldBe 1000
    lexemeCount(index) shouldBe 2000
    lexemeRelOffset(index) shouldBe 300000
  }

  @Test
  fun testGrowId() = ASTMarkersImpl().run {
    var index = pushBack()
    setMarker(index, 1, MarkerKind.Start, false, null, null)
    setMarkersCount(index, 10)
    setLexemeInfo(index, 20, 30)

    kind(index) shouldBe MarkerKind.Start
    collapsed(index) shouldBe false
    errorMessage(index) shouldBe null
    id(index) shouldBe 1
    markersCount(index) shouldBe 10
    lexemeCount(index) shouldBe 20
    lexemeRelOffset(index) shouldBe 30

    index = pushBack()
    setMarker(index, 2000000, MarkerKind.End, false, null, null)
    setMarkersCount(index, 1000)
    setLexemeInfo(index, 2000, 3000)

    kind(0) shouldBe MarkerKind.Start
    collapsed(0) shouldBe false
    errorMessage(0) shouldBe null
    id(0) shouldBe 1
    markersCount(0) shouldBe 10
    lexemeCount(0) shouldBe 20
    lexemeRelOffset(0) shouldBe 30

    kind(index) shouldBe MarkerKind.End
    collapsed(index) shouldBe false
    errorMessage(index) shouldBe null
    id(index) shouldBe 2000000
    markersCount(index) shouldBe 1000
    lexemeCount(index) shouldBe 2000
    lexemeRelOffset(index) shouldBe 3000
  }

  @Test
  fun testSubstitute() {
    val firstTree = ASTMarkersImpl().apply {
      pushBack()
      setMarker(0, 0, MarkerKind.Start, false, null, null)
      setMarkersCount(0, 1)
      setLexemeInfo(0, 1, 1)
      pushBack()
      setMarker(1, 1, MarkerKind.Start, false, null, null)
      setMarkersCount(1, 1)
      pushBack()
      setMarker(2, 1, MarkerKind.End, false, null, null)
      pushBack()
      setMarker(3, 0, MarkerKind.End, false, null, null)
    }


    val secondTree = ASTMarkersImpl().apply {
      pushBack()
      setMarker(0, 0, MarkerKind.Start, false, null, null)
      setLexemeInfo(0, 2, 2)
      pushBack()
      setMarker(1, 1, MarkerKind.Start, false, null, null)
      setLexemeInfo(1, 3, 3)
      pushBack()
      setMarker(2, 1, MarkerKind.End, false, null, null)
      pushBack()
      setMarker(3, 0, MarkerKind.End, false, null, null)
    }


    val result = firstTree.mutate { substitute(1, 1, secondTree) }

    result.run {
      size shouldBe 6
      lexemeCount(0) shouldBe 1
      lexemeCount(1) shouldBe 2
      lexemeCount(2) shouldBe 3
    }
  }

  @Test
  fun testSubstituteChameleonRemoved() {
    val atomicReference = newChameleonRef()
    val firstTree = ASTMarkersImpl().apply {
      pushBack()
      setMarker(0, 0, MarkerKind.Start, false, null, null)
      setLexemeInfo(0, 1, 1)
      pushBack()
      setMarker(1, 1, MarkerKind.Start, false, null, null)
      setMarkersCount(1, 1)
      pushBack()
      setMarker(2, 1, MarkerKind.End, false, null, null)
      pushBack()
      setMarker(3, 0, MarkerKind.End, false, null, null)
      setChameleon(1, atomicReference)
    }

    firstTree.chameleonAt(1) shouldBe atomicReference

    val secondTree = ASTMarkersImpl().apply {
      pushBack()
      setMarker(0, 0, MarkerKind.Start, false, null, null)
      setLexemeInfo(0, 2, 2)
      pushBack()
      setMarker(1, 1, MarkerKind.Start, false, "foo", null)
      setLexemeInfo(1, 3, 3)
      pushBack()
      setMarker(2, 1, MarkerKind.End, false, "foo", null)
      pushBack()
      setMarker(3, 0, MarkerKind.End, false, null, null)
    }

    val substitute = firstTree.mutate { substitute(1, 1, secondTree) }
    assertFailsWith<NullPointerException> { substitute.chameleonAt(1) }
  }

  @Test
  fun testSubstituteErrorRemoved() {
    val firstTree = ASTMarkersImpl().apply {
      pushBack()
      setMarker(0, 0, MarkerKind.Start, false, null, null)
      setLexemeInfo(0, 1, 1)
      pushBack()
      setMarker(1, 1, MarkerKind.Start, false, "foo", null)
      pushBack()
      setMarker(2, 1, MarkerKind.End, false, "foo", null)
      pushBack()
      setMarker(3, 0, MarkerKind.End, false, null, null)
    }

    firstTree.errorMessage(1) shouldBe "foo"

    val secondTree = ASTMarkersImpl().apply {
      pushBack()
      setMarker(0, 0, MarkerKind.Start, false, null, null)
      setLexemeInfo(0, 2, 2)
      pushBack()
      setMarker(1, 1, MarkerKind.Start, false, null, null)
      setLexemeInfo(1, 3, 3)
      pushBack()
      setMarker(2, 1, MarkerKind.End, false, null, null)
      pushBack()
      setMarker(3, 0, MarkerKind.End, false, null, null)
    }

    val result = firstTree.mutate { substitute(1, 1, secondTree) }

    result.errorMessage(1) shouldBe null
  }


  @Test
  fun testSubstituteChameleonCopied() {
    val firstTree = ASTMarkersImpl().apply {
      pushBack()
      setMarker(0, 0, MarkerKind.Start, false, null, null)
      setLexemeInfo(0, 1, 1)

      pushBack()
      setMarker(1, 1, MarkerKind.Start, false, null, null)

      pushBack()
      setMarker(2, 1, MarkerKind.End, false, null, null)

      pushBack()
      setMarker(3, 0, MarkerKind.End, false, null, null)
    }

    val atomicReference = newChameleonRef()
    val secondTree = ASTMarkersImpl().apply {
      pushBack()
      setMarker(0, 0, MarkerKind.Start, false, null, null)
      setLexemeInfo(0, 2, 2)

      pushBack()
      setMarker(1, 1, MarkerKind.Start, true, "foo", SyntaxTokenTypes.BAD_CHARACTER)
      setLexemeInfo(1, 3, 3)
      setChameleon(1, atomicReference)

      pushBack()
      setMarker(2, 1, MarkerKind.End, false, "foo", null)

      pushBack()
      setMarker(3, 0, MarkerKind.End, false, null, null)
    }

    val result = firstTree.mutate { substitute(1, 1, secondTree) }
    result.chameleonAt(2) shouldBe atomicReference
  }

  @Test
  fun testSubstituteErrorCopied() {
    val firstTree = ASTMarkersImpl().apply {
      pushBack()
      setMarker(0, 0, MarkerKind.Start, false, null, null)
      setLexemeInfo(0, 1, 1)
      pushBack()
      setMarker(1, 1, MarkerKind.Start, false, null, null)
      pushBack()
      setMarker(2, 1, MarkerKind.End, false, null, null)
      pushBack()
      setMarker(3, 0, MarkerKind.End, false, null, null)

    }
    firstTree.errorMessage(2) shouldBe null

    val secondTree = ASTMarkersImpl().apply {
      pushBack()
      setMarker(0, 0, MarkerKind.Start, false, null, null)
      setLexemeInfo(0, 2, 2)
      pushBack()
      setMarker(1, 1, MarkerKind.Start, false, "foo", null)
      setLexemeInfo(1, 3, 3)
      pushBack()
      setMarker(2, 1, MarkerKind.End, false, "foo", null)
      pushBack()
      setMarker(3, 0, MarkerKind.End, false, null, null)
    }

    val result = firstTree.mutate { substitute(1, 1, secondTree) }
    result.errorMessage(2) shouldBe "foo"
  }

  @Test
  fun testRenumbered() {
    val firstTree = ASTMarkersImpl().apply {
      pushBack()
      setMarker(0, Char.MAX_VALUE.code - 1, MarkerKind.Start, false, null, null)
      setMarkersCount(0, 1)
      setLexemeInfo(0, 1, 1)
      pushBack()
      setMarker(1, Char.MAX_VALUE.code, MarkerKind.Start, false, null, null)
      setMarkersCount(1, 1)
      pushBack()
      setMarker(2, Char.MAX_VALUE.code, MarkerKind.End, false, null, null)
      pushBack()
      setMarker(3, Char.MAX_VALUE.code - 1, MarkerKind.End, false, null, null)
    }

    val secondTree = ASTMarkersImpl().apply {
      pushBack()
      setMarker(0, 0, MarkerKind.Start, false, null, null)
      setLexemeInfo(0, 2, 2)
      pushBack()
      setMarker(1, 1, MarkerKind.Start, false, "foo", null)
      setLexemeInfo(1, 3, 3)
      pushBack()
      setMarker(2, 1, MarkerKind.End, false, "foo", null)
      pushBack()
      setMarker(3, 0, MarkerKind.End, false, null, null)
    }

    val result = firstTree.mutate { substitute(1, 1, secondTree) }

    result.id(0) shouldBe 0
    result.id(1) shouldBe 1
    result.id(2) shouldBe 2
  }

  // Makes it slightly more readable.
  private infix fun <T> T.shouldBe(expected: T) {
    assertEquals(expected, this)
  }
}