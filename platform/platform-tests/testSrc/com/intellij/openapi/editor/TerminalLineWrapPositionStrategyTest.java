// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import org.jetbrains.plugins.terminal.block.reworked.lang.TerminalLineWrapPositionStrategy;
import org.junit.Before;
import org.junit.Test;

public class TerminalLineWrapPositionStrategyTest extends AbstractLineWrapPositionStrategyTest {
  private LineWrapPositionStrategy myStrategy;

  @Override
  @Before
  public void prepare() {
    super.prepare();
    myStrategy = new TerminalLineWrapPositionStrategy();
  }

  //uD852 and uDF62 are surrogate pair - 2 characters that made 1 "𤭢"; soft wrap shouldn't be made between them
  //the expected result is to move the 3rd symbol to the next line, so we will have  uD852 uDF62\n uD852 uDF62 = 𤭢\n𤭢
  @Test
  public void preventWrapInsideOfSurrogatePairs() {
    String text = "\uD852\uDF62\uD852\uDF62";//𤭢𤭢
    doTestDefaultWrap(myStrategy, text, 3);
  }
}
