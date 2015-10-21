package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jun 30, 2004
 * Time: 5:07:33 PM
 * To change this template use File | Settings | File Templates.
 */
public class LiteralWithSubstitutionHandler extends MatchingHandler {
  private final String matchExpression;
  private Matcher matcher;
  private final List<SubstitutionHandler> handlers;

  public LiteralWithSubstitutionHandler(String _matchedExpression, List<SubstitutionHandler> _handlers) {
    matchExpression = _matchedExpression;
    handlers = _handlers;
  }

  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    final String text = matchedNode.getText();
    if (matcher==null) {
      matcher = Pattern.compile(matchExpression).matcher(text);
    } else {
      matcher.reset(text);
    }

    if (!matcher.find()) {
      return false;
    }
    for (int i = 0; i < handlers.size(); ++i) {
      final SubstitutionHandler handler = handlers.get(i);

      if (!handler.handle(matchedNode, matcher.start(i + 1), matcher.end(i + 1), context)) {
        return false;
      }
    }
    return true;
  }
}
