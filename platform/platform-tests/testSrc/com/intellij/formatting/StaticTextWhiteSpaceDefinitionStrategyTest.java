// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.psi.formatter.StaticTextWhiteSpaceDefinitionStrategy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertSame;

public class StaticTextWhiteSpaceDefinitionStrategyTest {

  private StaticTextWhiteSpaceDefinitionStrategy myStrategy;  

  @Before
  public void setUp() {
    myStrategy = new StaticTextWhiteSpaceDefinitionStrategy("abc");
  }

  @Test
  public void notAtFirstPosition() {
    assertSame(0, myStrategy.check(" abc", 0, 4));
    assertSame(1, myStrategy.check("  abc", 1, 3));
  }

  @Test
  public void match() {
    assertSame(3, myStrategy.check("abc", 0, 3));
    assertSame(4, myStrategy.check(" abcde", 1, 5));
  }

  @Test
  public void withoutEnd() {
    assertSame(0, myStrategy.check("abc", 0, 2));
    assertSame(1, myStrategy.check(" abc", 1, 3));
  }
}
