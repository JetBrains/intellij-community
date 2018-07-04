// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.Stack;

import static com.intellij.util.ArrayUtil.indexOf;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.KEYWORDS;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.LEFT_BRACES;

public abstract class GroovyLexerBase implements FlexLexer {

  // see groovy.g: allowRegexpLiteral()
  private final static TokenSet DIVISION_IS_EXPECTED_AFTER = TokenSet.orSet(KEYWORDS, TokenSet.create(
    mINC, mDEC,
    mRPAREN, mRBRACK, mRCURLY,
    mSTRING_LITERAL, mGSTRING_LITERAL, mGSTRING_END, mREGEX_END, mDOLLAR_SLASH_REGEX_END,
    mNUM_INT, mNUM_FLOAT, mNUM_DOUBLE, mNUM_BIG_INT, mNUM_BIG_DECIMAL,
    mIDENT,
    mDOLLAR
  ));

  public final Stack<Integer> stateStack = new Stack<>();
  private final Stack<IElementType> bracesStack = new Stack<>();

  protected void yybeginstate(int... states) {
    for (int state : states) {
      stateStack.push(state);
      yybegin(state);
    }
  }

  protected void yyendstate(int... states) {
    for (int state : states) {
      int previous = stateStack.isEmpty() ? getInitialState() : stateStack.pop();
      if (previous != state) {
        throw new RuntimeException("States does not match: previous=" + previous + ", expected=" + state);
      }
    }
    yybegin(stateStack.isEmpty() ? getInitialState() : stateStack.peek());
  }

  protected void resetState() {
    stateStack.clear();
    bracesStack.clear();
  }

  protected IElementType storeToken(IElementType tokenType) {
    if (LEFT_BRACES.contains(tokenType)) {
      bracesStack.push(tokenType);
    }
    else if (tokenType == mRCURLY) {
      IElementType leftType = mLCURLY;
      while (!bracesStack.isEmpty() && leftType != bracesStack.peek()) {
        bracesStack.pop();
      }
      if (!bracesStack.isEmpty() && leftType == bracesStack.peek()) {
        bracesStack.pop();
      }
    }
    else if (tokenType == mRPAREN || tokenType == mRBRACK) {
      if (!bracesStack.isEmpty() && bracesStack.peek() != mLCURLY) {
        bracesStack.pop();
      }
    }
    if (indexOf(getDivisionStates(), yystate()) != -1 && DIVISION_IS_EXPECTED_AFTER.contains(tokenType)) {
      yybeginstate(getDivisionExpectedState());
    }
    return tokenType;
  }

  protected boolean isWithinBraces() {
    return !bracesStack.empty() && bracesStack.peek() != mLCURLY;
  }

  protected abstract int getInitialState();

  protected abstract int[] getDivisionStates();

  protected abstract int getDivisionExpectedState();
}
