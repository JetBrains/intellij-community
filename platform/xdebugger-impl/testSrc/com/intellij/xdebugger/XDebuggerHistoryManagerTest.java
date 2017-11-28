/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xdebugger;

import com.intellij.lang.Language;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.impl.XDebuggerHistoryManager;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class XDebuggerHistoryManagerTest extends PlatformTestCase {
  public void testSerialize() {
    XDebuggerHistoryManager manager = XDebuggerHistoryManager.getInstance(getProject());
    assertNotNull(manager);

    manager.addRecentExpression("id1", new XExpressionImpl("0", null, "custom info", EvaluationMode.EXPRESSION));
    manager.addRecentExpression("id1", new XExpressionImpl("1", Language.ANY, null, EvaluationMode.CODE_FRAGMENT));

    manager.addRecentExpression("id2", new XExpressionImpl("10", null, null, EvaluationMode.EXPRESSION));

    Element state = manager.getState();

    // modify the state
    manager.addRecentExpression("id1", new XExpressionImpl("2", null, null, EvaluationMode.EXPRESSION));
    manager.addRecentExpression("id3", new XExpressionImpl("3", null, null, EvaluationMode.EXPRESSION));

    // rollback to an old state
    manager.loadState(state);

    List<XExpression> expressionsByFirstId = manager.getRecentExpressions("id1");
    assertEquals(2, expressionsByFirstId.size());
    checkExpression(expressionsByFirstId.get(0), "1", Language.ANY, null, EvaluationMode.CODE_FRAGMENT);
    checkExpression(expressionsByFirstId.get(1), "0", null, "custom info", EvaluationMode.EXPRESSION);

    List<XExpression> expressionsBySecondId = manager.getRecentExpressions("id2");
    assertEquals(1, expressionsBySecondId.size());
    checkExpression(expressionsBySecondId.get(0), "10", null, null, EvaluationMode.EXPRESSION);

    List<XExpression> expressionsByThirdId = manager.getRecentExpressions("id3");
    assertTrue(expressionsByThirdId == null || expressionsByThirdId.isEmpty());
  }

  private static void checkExpression(@NotNull XExpression expression,
                                      String expressionString,
                                      Language language,
                                      String customInfo,
                                      EvaluationMode mode) {
    assertEquals(expressionString, expression.getExpression());
    assertEquals(language, expression.getLanguage());
    assertEquals(customInfo, expression.getCustomInfo());
    assertEquals(mode, expression.getMode());
  }
}