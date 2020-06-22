// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.FList;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * Tells whether a string matches a specific pattern. Allows for lowercase camel-hump matching.
 * Used in navigation, code completion, speed search etc.
 *
 * @see NameUtil#buildMatcher(String)
 *
 * Inheritors MUST override `matchingFragments` and `matchingDegree` methods,
 * they are not abstract for binary compatibility.
 */
abstract public class MinusculeMatcher implements Matcher {

  protected MinusculeMatcher() {}

  @NotNull
  abstract public String getPattern();

  @Override
  public boolean matches(@NotNull String name) {
    return matchingFragments(name) != null;
  }

  public FList<TextRange> matchingFragments(@NotNull String name) {
    throw new UnsupportedOperationException();
  }

  public int matchingDegree(@NotNull String name, boolean valueStartCaseMatch, @Nullable FList<? extends TextRange> fragments) {
    throw new UnsupportedOperationException();
  }

  public int matchingDegree(@NotNull String name, boolean valueStartCaseMatch) {
    return matchingDegree(name, valueStartCaseMatch, matchingFragments(name));
  }

  public int matchingDegree(@NotNull String name) {
    return matchingDegree(name, false);
  }

  public boolean isStartMatch(@NotNull String name) {
    FList<TextRange> fragments = matchingFragments(name);
    return fragments != null && isStartMatch(fragments);
  }

  public static boolean isStartMatch(@NotNull Iterable<? extends TextRange> fragments) {
    Iterator<? extends TextRange> iterator = fragments.iterator();
    return !iterator.hasNext() || iterator.next().getStartOffset() == 0;
  }
}
