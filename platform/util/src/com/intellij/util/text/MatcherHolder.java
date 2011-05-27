package com.intellij.util.text;

import com.intellij.psi.codeStyle.NameUtil;

/**
 * @author Konstantin Bulenkov
 */
public interface MatcherHolder {
  void setPatternMatcher(NameUtil.Matcher matcher);
}
