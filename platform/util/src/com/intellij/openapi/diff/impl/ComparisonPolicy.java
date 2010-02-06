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
package com.intellij.openapi.diff.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.highlighting.Util;
import com.intellij.openapi.diff.impl.processing.DiffCorrection;
import com.intellij.openapi.diff.impl.processing.Formatting;
import com.intellij.openapi.diff.impl.processing.Word;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.diff.Diff;

public abstract class ComparisonPolicy {
  private final String myName;

  protected ComparisonPolicy(final String name) {
    myName = name;
  }

  public DiffFragment[] buildFragments(String[] strings1, String[] strings2) {
    DiffFragmentBuilder builder = new DiffFragmentBuilder(strings1, strings2);
    Object[] wrappers1 = getWrappers(strings1);
    Object[] wrappers2 = getWrappers(strings2);
    Diff.Change change = Diff.buildChanges(wrappers1, wrappers2);
    return DiffFragmentBuilder.buildFragments(builder, Util.concatEquals(change, wrappers1, wrappers2));
  }

  public DiffFragment[] buildDiffFragmentsFromLines(String[] lines1, String[] lines2) {
    DiffFragmentBuilder builder = new DiffFragmentBuilder(lines1, lines2);
    Object[] wrappers1 = getLineWrappers(lines1);
    Object[] wrappers2 = getLineWrappers(lines2);
    Diff.Change change = Diff.buildChanges(wrappers1, wrappers2);
    return DiffFragmentBuilder.buildFragments(builder, change);
  }

  public static final ComparisonPolicy DEFAULT = new ComparisonPolicy(CommonBundle.message("comparison.policy.default.name")) {
    protected Object[] getWrappers(String[] strings) {
      return strings;
    }

    protected Object[] getLineWrappers(String[] lines) {
      return lines;
    }

    public DiffFragment createFragment(Word word1, Word word2) {
      return createFragment(word1.getText(), word2.getText());
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "DEFAULT";
    }
  };

  public static final ComparisonPolicy TRIM_SPACE = new ComparisonPolicy(CommonBundle.message("comparison.policy.trim.space.name")) {
    protected Object[] getLineWrappers(String[] lines) {
      return trimStrings(lines);
    }

    public DiffFragment createFragment(Word word1, Word word2) {
      String text1 = word1.getText();
      String text2 = word2.getText();
      if (word1.isWhitespace() && word2.isWhitespace() &&
          word1.atEndOfLine() && word2.atEndOfLine()) {
        return DiffFragment.unchanged(text1, text2);
      }
      return createFragment(text1, text2);
    }

    protected Object[] getWrappers(String[] strings) {
      Object[] result = new Object[strings.length];
      boolean atBeginning = true;
      for (int i = 0; i < strings.length; i++) {
        String string = strings[i];
        String wrapper = atBeginning ? trimLeading(string) : string;
        if (StringUtil.endsWithChar(wrapper, '\n')) {
          atBeginning = true;
          wrapper = trimTrailing(wrapper);
        }
        else {
          atBeginning = false;
        }
        result[i] = wrapper;
      }
      return result;
    }

    private String trimLeading(String string) {
      int index = 0;
      while (index < string.length() && Character.isWhitespace(string.charAt(index))) index++;
      return string.substring(index);
    }

    private String trimTrailing(String string) {
      int index = string.length() - 1;
      while (index >= 0 && Character.isWhitespace(string.charAt(index))) index--;
      return string.substring(0, index + 1);
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "TRIM";
    }
  };

  public static final ComparisonPolicy IGNORE_SPACE = new IgnoreSpacePolicy();

  private static String toNotNull(String text) {
    return text == null ? "" : text;
  }

  protected abstract Object[] getWrappers(String[] strings);

  protected abstract Object[] getLineWrappers(String[] lines);

  protected Object[] trimStrings(String[] strings) {
    Object[] result = new Object[strings.length];
    for (int i = 0; i < strings.length; i++) {
      String string = strings[i];
      result[i] = string.trim();
    }
    return result;
  }

  public DiffFragment createFragment(String text1, String text2) {
    text1 = toNull(text1);
    text2 = toNull(text2);
    if (text1 == null && text2 == null) return new DiffFragment("", "");
    DiffFragment result = new DiffFragment(text1, text2);
    if (text1 != null && text2 != null) {
      result.setModified(!getWrapper(text1).equals(getWrapper(text2)));
    }
    return result;
  }

  private String toNull(String text1) {
    return text1 == null || text1.length() == 0 ? null : text1;
  }

  private Object getWrapper(String text) {
    return getWrappers(new String[]{text})[0];
  }

  public boolean isEqual(DiffFragment fragment) {
    if (fragment.isOneSide()) return false;
    Object[] wrappers = getLineWrappers(new String[]{fragment.getText1(), fragment.getText2()});
    return Comparing.equal(wrappers[0], wrappers[1]);
  }

  public Word createFormatting(String text, TextRange textRange) {
    return new Formatting(text, textRange);
  }

  public abstract DiffFragment createFragment(Word word1, Word word2);

  public String getName() {
    return myName;
  }

  public static final ComparisonPolicy[] COMPARISON_POLICIES = new ComparisonPolicy[]{DEFAULT, IGNORE_SPACE, TRIM_SPACE};
  
  public static ComparisonPolicy[] getAllInstances() {
    return COMPARISON_POLICIES;
  }

  private static class IgnoreSpacePolicy extends ComparisonPolicy implements DiffCorrection.FragmentProcessor<DiffCorrection.FragmentsCollector> {
    public IgnoreSpacePolicy() {
      super(CommonBundle.message("comparison.policy.ignore.spaces.name"));
    }

    protected Object[] getLineWrappers(String[] lines) {
      Object[] result = new Object[lines.length];
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        result[i] = getWrapper(line);
      }
      return result;
    }

    public DiffFragment[] buildFragments(String[] strings1, String[] strings2) {
      DiffFragment[] fragments = super.buildFragments(strings1, strings2);
      DiffCorrection.FragmentsCollector collector = new DiffCorrection.FragmentsCollector();
      collector.processAll(fragments, this);
      return collector.toArray();
    }

    private Object getWrapper(String line) {
      line = line.trim();
      char[] chars = new char[line.length()];
      line.getChars(0, line.length(), chars, 0);
      char[] result = new char[chars.length];
      int resultLength  = 0;
      for (int i = 0; i < chars.length; i++) {
        char aChar = chars[i];
        if (Character.isWhitespace(aChar)) continue;
        result[resultLength] = aChar;
        resultLength++;
      }
      return new String(result, 0, resultLength);
    }

    public DiffFragment createFragment(Word word1, Word word2) {
      String text1 = word1.getText();
      String text2 = word2.getText();
      return word1.isWhitespace() && word2.isWhitespace() ?
             DiffFragment.unchanged(text1, text2) :
             createFragment(text1, text2);
    }

    public DiffFragment createFragment(String text1, String text2) {
      String toCompare1 = toNotNull(text1);
      String toCompare2 = toNotNull(text2);
      if (getWrapper(toCompare1).equals(getWrapper(toCompare2))) {
        return DiffFragment.unchanged(toCompare1, toCompare2);
      }
      return new DiffFragment(text1, text2);
    }

    protected Object[] getWrappers(String[] strings) {
      return trimStrings(strings);
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "IGNORE";
    }

    public void process(DiffFragment fragment, DiffCorrection.FragmentsCollector collector) {
      if (fragment.isEqual()) {
        collector.add(fragment);
        return;
      }
      if (fragment.isOneSide()) {
        FragmentSide side = FragmentSide.chooseSide(fragment);
        String text = side.getText(fragment);
        String trimed = text.trim();
        if (trimed.length() == 0) {
          collector.add(side.createFragment(text, "", false));
          return;
        }
      }
      collector.add(fragment);
    }
  }
}
