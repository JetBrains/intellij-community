/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogDetailsFilter;
import com.intellij.vcs.log.VcsLogTextFilter;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class VcsLogTextFilterImpl implements VcsLogDetailsFilter, VcsLogTextFilter {

  @NotNull private final String myText;
  private final boolean myMatchCase;
  @Nullable private final Pattern myPattern;

  public VcsLogTextFilterImpl(@NotNull String text, boolean isRegexAllowed, boolean matchCase) {
    myText = text;
    myMatchCase = matchCase;
    myPattern = createPattern(myText, isRegexAllowed, myMatchCase);
  }

  @Nullable
  private static Pattern createPattern(@NotNull String text, boolean isRegexAllowed, boolean matchCase) {
    if (isRegexAllowed && VcsLogUtil.maybeRegexp(text)) {
      try {
        return matchCase ? Pattern.compile(text) : Pattern.compile(text, Pattern.CASE_INSENSITIVE);
      }
      catch (PatternSyntaxException ignored) {
      }
    }
    return null;
  }

  // used in upsource
  @SuppressWarnings("unused")
  public VcsLogTextFilterImpl(@NotNull String text) {
    this(text, false, false);
  }

  @Override
  public boolean matches(@NotNull VcsCommitMetadata details) {
    return matches(this, details.getFullMessage());
  }

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  @Override
  public boolean isRegex() {
    return myPattern != null;
  }

  @Override
  public boolean matchesCase() {
    return myMatchCase;
  }

  @Override
  public String toString() {
    return (isRegex() ? "matching " : "containing ") + myText + " (case " + (myMatchCase ? "sensitive" : "insensitive") + ")";
  }

  public static boolean matches(@NotNull VcsLogTextFilter filter, @NotNull String message) {
    Pattern pattern;
    if (filter instanceof VcsLogTextFilterImpl) {
      pattern = ((VcsLogTextFilterImpl)filter).myPattern;
    }
    else {
      pattern = createPattern(filter.getText(), filter.isRegex(), filter.matchesCase());
    }
    if (pattern != null) return pattern.matcher(message).find();

    if (filter.matchesCase()) return message.contains(filter.getText());
    return StringUtil.containsIgnoreCase(message, filter.getText());
  }
}
