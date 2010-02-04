/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.diff.Diff;
import gnu.trove.TIntHashSet;

import java.util.StringTokenizer;

public class Util {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.highlighting.Util");
  private static final String DELIMITERS = " \n\r\t(){}[],./?`~!@#$%^&*-=+|\\;:'\"<>";
  public static final TIntHashSet DELIMITERS_SET = new TIntHashSet();

  static {
    char[] delimiters = Util.DELIMITERS.toCharArray();
    for (int i = 0; i < delimiters.length; i++) {
      char delimiter = delimiters[i];
      Util.DELIMITERS_SET.add(delimiter);
    }
  }

  static String[] splitByWord(String string) {
    BufferedStringList stringList = new BufferedStringList();
    StringTokenizer tokenizer = new StringTokenizer(string, DELIMITERS, true);
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if (token.length() == 1 && DELIMITERS_SET.contains(token.charAt(0))) {
        char delimiter = token.charAt(0);
        if (delimiter == '\n') {
          stringList.appendToLast(token);
          stringList.flushLast();
          continue;
        }
        if (Character.isWhitespace(delimiter)) {
          stringList.appendToLast(token);
          continue;
        }
      }
      stringList.add(token);
    }
    return stringList.toArray();
  }

  static boolean isSpaceOnly(DiffFragment fragment) {
    return isSpaceOnly(fragment.getText1()) && isSpaceOnly(fragment.getText2());
  }

  private static boolean isSpaceOnly(String string) {
    if (string == null) return true;
    for (int i = 0; i < string.length(); i++) if (!Character.isWhitespace(string.charAt(i))) return false;
    return true;
  }

  static DiffFragment[] splitByLines(DiffFragment fragment) {
    String[] lines1 = splitByLines(fragment.getText1());
    String[] lines2 = splitByLines(fragment.getText2());
    if (lines1 != null && lines2 != null && lines1.length != lines2.length) {
      LOG.assertTrue(false, "1:<" + fragment.getText1() + "> 2:<" + fragment.getText2() + ">");
    }
    int length = lines1 == null ? lines2.length : lines1.length;
    DiffFragment[] lines = new DiffFragment[length];
    for (int i = 0; i < lines.length; i++) {
      lines[i] = new DiffFragment(lines1 == null? null : lines1[i], lines2 == null ? null : lines2[i]);
    }
    return lines;
  }

  private static String[] splitByLines(String string) {
    if (string == null) return null;
    if (string.indexOf('\n') == -1) return new String[]{string};
    String[] strings = string.split("\n", -1);
    for (int i = 0; i < strings.length - 1; i++) {
      strings[i] += "\n";
    }
    if (StringUtil.endsWithChar(string, '\n')) {
      String[] result = new String[strings.length - 1];
      System.arraycopy(strings, 0, result, 0, strings.length - 1);
      return result;
    }
    return strings;
  }

  public static DiffFragment[][] splitByUnchangedLines(DiffFragment[] fragments) {
    List2D result = new List2D();
    for (int i = 0; i < fragments.length; i++) {
      DiffFragment fragment = fragments[i];
      if (!fragment.isEqual()) {
        result.add(fragment);
        continue;
      }
      String text1 = fragment.getText1();
      String text2 = fragment.getText2();
      if (StringUtil.endsWithChar(text1, '\n') && StringUtil.endsWithChar(text2, '\n')) {
        result.add(fragment);
        result.newRow();
        continue;
      }
      while (true) {
        int newLine1 = text1.indexOf('\n');
        int newLine2 = text2.indexOf('\n');
        if (newLine1 == -1 || newLine2 == -1) {
          result.add(DiffFragment.unchanged(text1, text2));
          break;
        }
        result.add(DiffFragment.unchanged(text1.substring(0, newLine1 + 1), text2.substring(0, newLine2 + 1)));
        result.newRow();
        text1 = text1.substring(newLine1 + 1);
        text2 = text2.substring(newLine2 + 1);
        int length1 = text1.length();
        int length2 = text2.length();
        if (length1 == 0 || length2 == 0) {
          if (length1 != 0 || length2 != 0)
            result.add(DiffFragment.unchanged(text1, text2));
          break;
        }
      }
    }
    return result.toArray();
  }

  public static Diff.Change concatEquals(Diff.Change change, Object[] left, Object[] right) {
    MyChange startChange = new MyChange(0, 0, 0, 0);
    MyChange lastChange = startChange;
    while (change != null) {
      if (change.inserted > 0 && change.deleted > 0) {
        lastChange = lastChange.copyNext(change);
      } else if (change.inserted > 0) {
        int shift = calcShift(right, lastChange.getEnd2(), change.line1, change.inserted);
        lastChange = lastChange.copyNext(change, shift);
      } else if (change.deleted > 0) {
        int shift = calcShift(left, lastChange.getEnd1(), change.line0, change.deleted);
        lastChange = lastChange.copyNext(change, shift);
      } else {
        LOG.assertTrue(false);
      }
      change = change.link;
    }
    return concatSingleSide(startChange.link);
  }

  private static Diff.Change concatSingleSide(Diff.Change change) {
    MyChange startChange = new MyChange(0, 0, 0, 0);
    MyChange lastChange = startChange;
    MyChange prevChange = null;
    while (change != null) {
      if (prevChange == null || (change.inserted > 0 && change.deleted > 0)) {
        prevChange = lastChange;
        lastChange = lastChange.copyNext(change);
      } else {
        MyChange newChange = null;
        if (change.deleted == 0 && lastChange.deleted == 0 && change.line1 == lastChange.getEnd2()) {
          newChange = new MyChange(lastChange.line0, lastChange.line1, 0, lastChange.inserted + change.inserted);
        } else if (change.inserted == 0 && lastChange.inserted == 0 && change.line0 == lastChange.getEnd1()) {
          newChange = new MyChange(lastChange.line0, lastChange.line1, lastChange.deleted + change.deleted, 0);
        }
        if (newChange != null) {
          prevChange.setNext(newChange);
          lastChange = newChange;
        } else {
          prevChange = lastChange;
          lastChange = lastChange.copyNext(change);
        }
      }
      change = change.link;
    }
    return startChange.link;
  }

  static int calcShift(Object[] list, int limit, int start, int length) {
    int shift = start - limit;
    for (int i = 0; i < shift; i++) {
      if (!list[limit + i].equals(list[start + length - shift + i])) return 0;
    }
    return -shift;
  }

  public static DiffFragment unite(DiffFragment fragment1, DiffFragment fragment2) {
    LOG.assertTrue(isSameType(fragment1, fragment2));
    if (!fragment1.isOneSide()) {
      String unitedText1 = fragment1.getText1() + fragment2.getText1();
      String unitedText2 = fragment1.getText2() + fragment2.getText2();
      LOG.assertTrue(fragment1.isEqual() == fragment2.isEqual());
      return fragment1.isEqual() ? DiffFragment.unchanged(unitedText1, unitedText2) :
          new DiffFragment(unitedText1, unitedText2);
    }
    FragmentSide side = FragmentSide.chooseSide(fragment1);
    return side.createFragment(side.getText(fragment1) + side.getText(fragment2), null, fragment1.isModified());
  }

  public static boolean isSameType(DiffFragment fragment1, DiffFragment fragment2) {
    if (fragment1.isEqual()) return fragment2.isEqual();
    if (fragment1.isChange()) return fragment2.isChange();
    if (fragment1.getText1() == null) return fragment2.getText1() == null;
    if (fragment1.getText2() == null) return fragment2.getText2() == null;
    LOG.assertTrue(false);
    return false;
  }

  public static String getText(DiffFragment[] fragments, FragmentSide side) {
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < fragments.length; i++) {
      DiffFragment fragment = fragments[i];
      String text = side.getText(fragment);
      if (text != null) buffer.append(text);
    }
    return buffer.toString();
  }

  public static DiffFragment concatenate(DiffFragment[] line) {
    return concatenate(line, 0, line.length);
  }

  public static DiffFragment concatenate(DiffFragment[] line, int from, int to) {
    StringBuffer buffer1 = new StringBuffer();
    StringBuffer buffer2 = new StringBuffer();
    boolean isEqual = true;
    for (int j = from; j < to; j++) {
      DiffFragment fragment = line[j];
      isEqual &= fragment.isEqual();
      String text1 = fragment.getText1();
      String text2 = fragment.getText2();
      if (text1 != null) buffer1.append(text1);
      if (text2 != null) buffer2.append(text2);
    }
    String text1 = notEmptyContent(buffer1);
    String text2 = notEmptyContent(buffer2);
    return isEqual ? DiffFragment.unchanged(text1, text2) : new DiffFragment(text1, text2);
  }

  private static String notEmptyContent(StringBuffer buffer) {
    return buffer.length() > 0 ? buffer.toString() : null;
  }

  public static DiffFragment[][] uniteFormattingOnly(DiffFragment[][] lines) {
    List2D result = new List2D();
    for (int i = 0; i < lines.length; i++) {
      DiffFragment[] line = lines[i];
      if (!areEqual(line) && areEqualOrFormatting(line)) result.addAll(line);
      else {
        result.newRow();
        result.addAll(line);
        result.newRow();
      }
    }
    return result.toArray();
  }

  private static boolean areEqualOrFormatting(DiffFragment[] fragments) {
    for (int i = 0; i < fragments.length; i++) {
      DiffFragment fragment = fragments[i];
      if (fragment.isEqual()) continue;
      for (int side = 0;  side < 2; side++) {
        String text = FragmentSide.fromIndex(side).getText(fragment);
        if (text == null || text.trim().length() == 0) continue;
        return false;
      }
    }
    return true;
  }

  private static boolean areEqual(DiffFragment[] fragments) {
    for (int i = 0; i < fragments.length; i++) {
      DiffFragment fragment = fragments[i];
      if (!fragment.isEqual()) return false;
    }
    return true;
  }

  public static DiffFragment[] cutFirst(DiffFragment[] fragments) {
    int nullCount = 0;
    for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
      FragmentSide side = FragmentSide.fromIndex(sideIndex);
      for (int i = 0; i < fragments.length; i++) {
        DiffFragment fragment = fragments[i];
        if (fragment == null) continue;
        String text = side.getText(fragment);
        if (text == null || text.length() == 0) continue;
        text = text.length() > 1 ? text.substring(1) : null;
        String otherText = side.getOtherText(fragment);
        if (otherText == null && text == null) {
          fragments[i] = null;
          nullCount++;
        } else fragments[i] = side.createFragment(text, otherText, fragment.isModified());
        break;
      }
    }
    if (nullCount == 0) return fragments;
    DiffFragment[] result = new DiffFragment[fragments.length - nullCount];
    int dstIndex = 0;
    for (int i = 0; i < fragments.length; i++) {
      DiffFragment fragment = fragments[i];
      if (fragment == null) continue;
      result[dstIndex] = fragment;
      dstIndex++;
    }
    return result;
  }

  private static class MyChange extends Diff.Change {
    public MyChange(int line0, int line1, int deleted, int inserted) {
      super(line0, line1, deleted, inserted, null);
    }

    public MyChange copyNext(Diff.Change change) {
      return copyNext(change, 0);
    }

    public MyChange copyNext(Diff.Change change, int shift) {
      MyChange result = new MyChange(change.line0 + shift, change.line1 + shift, change.deleted, change.inserted);
      setNext(result);
      return result;
    }

    public void setNext(MyChange change) {
      link = change;
    }

    public int getEnd1() {
      return line0 + deleted;
    }

    public int getEnd2() {
      return line1 + inserted;
    }
  }
}
