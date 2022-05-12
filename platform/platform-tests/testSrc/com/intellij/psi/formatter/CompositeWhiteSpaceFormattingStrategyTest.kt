// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter

import org.junit.Assert
import org.junit.Before
import org.junit.Test

class CompositeWhiteSpaceStrategyTest {

  private lateinit var myStrategy: CompositeWhiteSpaceFormattingStrategy

  @Before
  fun setUp() {
    myStrategy = CompositeWhiteSpaceFormattingStrategy(listOf(
      StaticSymbolWhiteSpaceDefinitionStrategy('a', 'c'),
      StaticSymbolWhiteSpaceDefinitionStrategy('a', 'b')
    ))
  }

  @Test
  fun failOnTheFirstSymbol() {
    Assert.assertSame(0, myStrategy.check("daaaaa", 0, 2))
    Assert.assertSame(1, myStrategy.check("adaaaa", 1, 2))
  }

  @Test
  fun failInTheMiddle() {
    Assert.assertSame(1, myStrategy.check("adbc", 0, 3))
    Assert.assertSame(4, myStrategy.check("cabcdcb", 1, 5))
  }

  @Test
  fun failOnTheLastSymbol() {
    Assert.assertSame(2, myStrategy.check("aad", 0, 3))
    Assert.assertSame(3, myStrategy.check("caadabd", 1, 4))
  }

  @Test
  fun successfulMatch() {
    Assert.assertSame(3, myStrategy.check("aca", 0, 3))
    Assert.assertSame(4, myStrategy.check("babcbda", 1, 4))
  }
}