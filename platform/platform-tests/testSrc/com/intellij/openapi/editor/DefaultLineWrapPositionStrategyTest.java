/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor;

import org.junit.Before;
import org.junit.Test;

/**
 * @author Denis Zhdanov
 */
public class DefaultLineWrapPositionStrategyTest extends AbstractLineWrapPositionStrategyTest {
  private LineWrapPositionStrategy myStrategy;

  @Override
  @Before
  public void prepare() {
    super.prepare();
    myStrategy = new DefaultLineWrapPositionStrategy();
  }

  @Test
  public void commaNotSeparated() {
    String document =
      "void method(String <WRAP>p1<EDGE>, String p2) {}";
    doTest(myStrategy, document, false);
  }

  @Test
  public void wrapOnExceedingWhiteSpace() {
    String document =
      "void method(String p1,<WRAP><EDGE> String p2) {}";
    doTest(myStrategy, document);
  }

  @Test
  public void doNotPreferWrapOnComma() {
    String document =
      "int variable = testMethod(var1 + var2, var3 + <WRAP>va<EDGE>r4);";
    doTest(myStrategy, document);
  }

  @Test
  public void longStringWithoutWrapPositionIsNotWrapped() {
    String document =
      "-----------------<EDGE>---------------------------------------------------------";
    doTest(myStrategy, document);
  }

  @Test
  public void preferNearestWhiteSpaceWrap_InsteadOf_Comma() {
    String document =
      "queueing the JSON for later submission, we retain the <WRAP>SimpleRequestDa<EDGE>ta";
    doTest(myStrategy, document, false);
  }
}
