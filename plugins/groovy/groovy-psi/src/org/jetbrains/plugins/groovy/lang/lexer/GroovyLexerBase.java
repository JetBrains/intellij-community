// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.Stack;

import static com.intellij.util.ArrayUtil.indexOf;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.KEYWORDS;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets.STRING_LITERALS;

public abstract class GroovyLexerBase implements FlexLexer {

  // see groovy.g: allowRegexpLiteral()
  private final static TokenSet DIVISION_IS_EXPECTED_AFTER = TokenSet.orSet(KEYWORDS, STRING_LITERALS, TokenSet.create(
    mINC, mDEC,
    mRPAREN, mRBRACK, mRCURLY,
    mGSTRING_END, mREGEX_END, mDOLLAR_SLASH_REGEX_END,
    mNUM_INT, mNUM_FLOAT, mNUM_DOUBLE, mNUM_BIG_INT, mNUM_BIG_DECIMAL,
    mIDENT,
    mDOLLAR
  ));

  public final Stack<Integer> stateStack = new Stack<>();

  protected void resetState() {
    stateStack.clear();
  }

  protected void yybeginstate(int... states) {
    assert states.length > 0;
    for (int state : states) {
      assert state != getInitialState();
      stateStack.push(state);
      yybegin(state);
    }
  }

  protected void yyendstate(int... states) {
    for (int state : states) {
      assert state != getInitialState();
      assert !stateStack.isEmpty() : stateStack;
      int previous = stateStack.pop();
      assert previous == state : "States does not match: previous=" + previous + ", expected=" + state;
    }
    yybegin(stateStack.isEmpty() ? getInitialState() : stateStack.peek());
  }

  protected IElementType storeToken(IElementType tokenType) {
    if (indexOf(getDivisionStates(), yystate()) != -1 && DIVISION_IS_EXPECTED_AFTER.contains(tokenType)) {
      yybeginstate(getDivisionExpectedState());
    }
    return tokenType;
  }

  protected abstract int getInitialState();

  protected abstract int[] getDivisionStates();

  protected abstract int getDivisionExpectedState();
}
