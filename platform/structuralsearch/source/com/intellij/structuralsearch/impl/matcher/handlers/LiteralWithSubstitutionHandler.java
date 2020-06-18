// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LiteralWithSubstitutionHandler extends MatchingHandler {
  private final String myRegexp;
  private Matcher myMatcher;
  private final List<? extends SubstitutionHandler> myHandlers;
  private final boolean myCaseSensitive;

  public LiteralWithSubstitutionHandler(String regexp, List<? extends SubstitutionHandler> handlers, boolean caseSensitive) {
    myRegexp = regexp;
    myHandlers = handlers;
    myCaseSensitive = caseSensitive;
  }

  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    return match(matchedNode, matchedNode.getText(), 0, context);
  }

  public boolean match(PsiElement matchedNode, String text, int textOffset, MatchContext context) {
    if (myMatcher == null) {
      myMatcher = Pattern.compile(myRegexp, (myCaseSensitive ? 0 : Pattern.CASE_INSENSITIVE) | Pattern.DOTALL).matcher(text);
    }
    else {
      myMatcher.reset(text);
    }

    if (!myMatcher.matches()) {
      return false;
    }
    for (int i = 0; i < myHandlers.size(); ++i) {
      final SubstitutionHandler handler = myHandlers.get(i);

      if (!handler.handle(matchedNode, textOffset + myMatcher.start(i + 1), textOffset + myMatcher.end(i + 1), context)) {
        return false;
      }
    }
    return true;
  }
}
