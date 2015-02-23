/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.diff.impl.string.DiffString;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public abstract class ComparisonPolicy {
  public static final ComparisonPolicy DEFAULT = new DefaultPolicy();
  public static final ComparisonPolicy TRIM_SPACE = new TrimSpacePolicy();
  public static final ComparisonPolicy IGNORE_SPACE = new IgnoreSpacePolicy();
  public static final ComparisonPolicy[] COMPARISON_POLICIES = new ComparisonPolicy[]{DEFAULT, IGNORE_SPACE, TRIM_SPACE};

  private final String myName;

  protected ComparisonPolicy(final String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  @NotNull
  public DiffFragment[] buildFragments(@NotNull DiffString[] strings1, @NotNull DiffString[] strings2) throws FilesTooBigForDiffException {
    DiffFragmentBuilder builder = new DiffFragmentBuilder(strings1, strings2);
    Object[] wrappers1 = getWrappers(strings1);
    Object[] wrappers2 = getWrappers(strings2);
    Diff.Change change = Diff.buildChanges(wrappers1, wrappers2);
    return builder.buildFragments(Util.concatEquals(change, wrappers1, wrappers2));
  }

  @NotNull
  public DiffFragment[] buildDiffFragmentsFromLines(@NotNull DiffString[] lines1, @NotNull DiffString[] lines2)
    throws FilesTooBigForDiffException {
    DiffFragmentBuilder builder = new DiffFragmentBuilder(lines1, lines2);
    Object[] wrappers1 = getLineWrappers(lines1);
    Object[] wrappers2 = getLineWrappers(lines2);
    Diff.Change change = Diff.buildChanges(wrappers1, wrappers2);
    return builder.buildFragments(change);
  }

  @NotNull
  public DiffFragment createFragment(@Nullable DiffString text1, @Nullable DiffString text2) {
    text1 = toNull(text1);
    text2 = toNull(text2);
    if (text1 == null && text2 == null) return new DiffFragment(DiffString.EMPTY, DiffString.EMPTY);
    DiffFragment result = new DiffFragment(text1, text2);
    if (text1 != null && text2 != null) {
      result.setModified(!getWrapper(text1).equals(getWrapper(text2)));
    }
    return result;
  }

  @NotNull
  public abstract DiffFragment createFragment(@NotNull Word word1, @NotNull Word word2);

  @NotNull
  protected abstract Object[] getWrappers(@NotNull DiffString[] strings);

  @NotNull
  protected abstract Object[] getLineWrappers(@NotNull DiffString[] lines);

  @NotNull
  private Object getWrapper(@NotNull DiffString text) {
    return getWrappers(new DiffString[]{text})[0];
  }

  private static class DefaultPolicy extends ComparisonPolicy {
    public DefaultPolicy() {
      super(CommonBundle.message("comparison.policy.default.name"));
    }

    @NotNull
    @Override
    protected Object[] getWrappers(@NotNull DiffString[] strings) {
      return strings;
    }

    @NotNull
    @Override
    protected Object[] getLineWrappers(@NotNull DiffString[] lines) {
      return lines;
    }

    @NotNull
    @Override
    public DiffFragment createFragment(@NotNull Word word1, @NotNull Word word2) {
      return createFragment(word1.getText(), word2.getText());
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "DEFAULT";
    }
  }

  private static class TrimSpacePolicy extends ComparisonPolicy {
    public TrimSpacePolicy() {
      super(CommonBundle.message("comparison.policy.trim.space.name"));
    }

    @NotNull
    @Override
    protected Object[] getLineWrappers(@NotNull DiffString[] lines) {
      return trimStrings(lines);
    }

    @NotNull
    @Override
    public DiffFragment createFragment(@NotNull Word word1, @NotNull Word word2) {
      DiffString text1 = word1.getText();
      DiffString text2 = word2.getText();
      if (word1.isWhitespace() && word2.isWhitespace() &&
          word1.atEndOfLine() && word2.atEndOfLine()) {
        return DiffFragment.unchanged(text1, text2);
      }
      return createFragment(text1, text2);
    }

    @NotNull
    @Override
    protected Object[] getWrappers(@NotNull DiffString[] strings) {
      Object[] result = new Object[strings.length];
      boolean atBeginning = true;
      for (int i = 0; i < strings.length; i++) {
        DiffString string = strings[i];
        DiffString wrapper = atBeginning ? string.trimLeading() : string;
        if (wrapper.endsWith('\n')) {
          atBeginning = true;
          wrapper = wrapper.trimTrailing();
        }
        else {
          atBeginning = false;
        }
        result[i] = wrapper;
      }
      return result;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "TRIM";
    }
  }

  private static class IgnoreSpacePolicy extends ComparisonPolicy
    implements DiffCorrection.FragmentProcessor<DiffCorrection.FragmentsCollector> {
    public IgnoreSpacePolicy() {
      super(CommonBundle.message("comparison.policy.ignore.spaces.name"));
    }

    @NotNull
    @Override
    protected Object[] getLineWrappers(@NotNull DiffString[] lines) {
      Object[] result = new Object[lines.length];
      for (int i = 0; i < lines.length; i++) {
        DiffString line = lines[i];
        result[i] = getWrapper(line);
      }
      return result;
    }

    @NotNull
    @Override
    public DiffFragment[] buildFragments(@NotNull DiffString[] strings1, @NotNull DiffString[] strings2)
      throws FilesTooBigForDiffException {
      DiffFragment[] fragments = super.buildFragments(strings1, strings2);
      DiffCorrection.FragmentsCollector collector = new DiffCorrection.FragmentsCollector();
      collector.processAll(fragments, this);
      return collector.toArray();
    }

    @NotNull
    private static Object getWrapper(@NotNull DiffString line) {
      return line.skipSpaces();
    }

    @NotNull
    @Override
    public DiffFragment createFragment(@NotNull Word word1, @NotNull Word word2) {
      DiffString text1 = word1.getText();
      DiffString text2 = word2.getText();
      return word1.isWhitespace() && word2.isWhitespace() ? DiffFragment.unchanged(text1, text2) : createFragment(text1, text2);
    }

    @NotNull
    @Override
    public DiffFragment createFragment(DiffString text1, DiffString text2) {
      DiffString toCompare1 = toNotNull(text1);
      DiffString toCompare2 = toNotNull(text2);
      if (getWrapper(toCompare1).equals(getWrapper(toCompare2))) {
        return DiffFragment.unchanged(toCompare1, toCompare2);
      }
      return new DiffFragment(text1, text2);
    }

    @NotNull
    @Override
    protected Object[] getWrappers(@NotNull DiffString[] strings) {
      return trimStrings(strings);
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "IGNORE";
    }

    @Override
    public void process(@NotNull DiffFragment fragment, @NotNull DiffCorrection.FragmentsCollector collector) {
      if (fragment.isEqual()) {
        collector.add(fragment);
        return;
      }
      if (fragment.isOneSide()) {
        FragmentSide side = FragmentSide.chooseSide(fragment);
        DiffString text = side.getText(fragment);
        if (StringUtil.isEmptyOrSpaces(text)) {
          collector.add(side.createFragment(text, DiffString.EMPTY, false));
          return;
        }
      }
      collector.add(fragment);
    }
  }

  @Nullable
  private static DiffString toNull(@Nullable DiffString text1) {
    return text1 == null || text1.isEmpty() ? null : text1;
  }

  @NotNull
  private static DiffString toNotNull(@Nullable DiffString text) {
    return text == null ? DiffString.EMPTY : text;
  }

  @NotNull
  protected Object[] trimStrings(@NotNull DiffString[] strings) {
    Object[] result = new Object[strings.length];
    for (int i = 0; i < strings.length; i++) {
      DiffString string = strings[i];
      result[i] = string.trim();
    }
    return result;
  }

  public boolean isEqual(@NotNull DiffFragment fragment) {
    if (fragment.isOneSide()) return false;
    Object[] wrappers = getLineWrappers(new DiffString[]{fragment.getText1(), fragment.getText2()});
    return Comparing.equal(wrappers[0], wrappers[1]);
  }

  @NotNull
  public Word createFormatting(@NotNull DiffString text, @NotNull TextRange textRange) {
    return new Formatting(text, textRange);
  }

  public static ComparisonPolicy[] getAllInstances() {
    return COMPARISON_POLICIES;
  }

  @NotNull
  @TestOnly
  protected Object[] getWrappers(@NotNull String[] lines) {
    DiffString[] unsafeStrings = new DiffString[lines.length];
    for (int i = 0; i < lines.length; i++) {
      unsafeStrings[i] = DiffString.createNullable(lines[i]);
    }
    return getWrappers(unsafeStrings);
  }

  @NotNull
  @TestOnly
  protected Object[] getLineWrappers(@NotNull String[] lines) {
    DiffString[] unsafeStrings = new DiffString[lines.length];
    for (int i = 0; i < lines.length; i++) {
      unsafeStrings[i] = DiffString.createNullable(lines[i]);
    }
    return getLineWrappers(unsafeStrings);
  }
}
