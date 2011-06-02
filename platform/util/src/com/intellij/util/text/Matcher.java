package com.intellij.util.text;

import com.intellij.psi.codeStyle.NameUtil;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface Matcher extends NameUtil.Matcher {
  boolean matches(String name);
}
