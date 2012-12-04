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
package com.intellij.util.diff;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Enumerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author dyoma
 */
public class Diff {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.diff.Diff");

  @Nullable
  public static Change buildChanges(@NotNull CharSequence before, @NotNull CharSequence after) throws FilesTooBigForDiffException {
    final String[] strings1 = LineTokenizer.tokenize(before, false);
    final String[] strings2 = LineTokenizer.tokenize(after, false);
    return buildChanges(strings1, strings2);
  }

  @Nullable
  public static <T> Change buildChanges(@NotNull T[] objects1, @NotNull T[] objects2) throws FilesTooBigForDiffException {

    // Old variant of enumerator worked incorrectly with null values.
    // This check is to ensure that the corrected version does not introduce bugs.
    for (T anObjects1 : objects1) LOG.assertTrue(anObjects1 != null);
    for (T anObjects2 : objects2) LOG.assertTrue(anObjects2 != null);

    final int startShift = getStartShift(objects1, objects2);
    final int endCut = getEndCut(objects1, objects2, startShift);

    Enumerator<T> enumerator = new Enumerator<T>(objects1.length + objects2.length, ContainerUtil.<T>canonicalStrategy());
    int[] ints1 = enumerator.enumerate(objects1, startShift, endCut);
    int[] ints2 = enumerator.enumerate(objects2, startShift, endCut);
    Reindexer reindexer = new Reindexer();
    int[][] discarded = reindexer.discardUnique(ints1, ints2);
    IntLCS intLCS = new IntLCS(discarded[0], discarded[1]);
    intLCS.execute();
    ChangeBuilder builder = new ChangeBuilder(startShift);
    reindexer.reindex(intLCS.getPaths(), builder);
    return builder.getFirstChange();
  }

  private static <T> int getStartShift(final T[] o1, final T[] o2) {
    final int size = Math.min(o1.length, o2.length);
    int idx = 0;
    for (int i = 0; i < size; i++) {
      if (! o1[i].equals(o2[i])) break;
      ++ idx;
    }
    return idx;
  }

  private static <T> int getEndCut(final T[] o1, final T[] o2, final int startShift) {
    final int size = Math.min(o1.length, o2.length) - startShift;
    int idx = 0;

    for (int i = 0; i < size; i++) {
      if (! o1[o1.length - i - 1].equals(o2[o2.length - i - 1])) break;
      ++ idx;
    }
    return idx;
  }

  /**
   * Tries to translate given line that pointed to the text before change to the line that points to the same text after the change.
   *
   * @param before    text before change
   * @param after     text after change
   * @param line      target line before change
   * @return          translated line if the processing is ok; negative value otherwise
   */
  public static int translateLine(@NotNull CharSequence before, @NotNull CharSequence after, int line) throws FilesTooBigForDiffException {
    Change change = buildChanges(before, after);
    if (change == null) {
      return -1;
    }
    return translateLine(change, line);
  }

  /**
   * Tries to translate given line that pointed to the text before change to the line that points to the same text after the change.
   * 
   * @param change    target change
   * @param line      target line before change
   * @return          translated line if the processing is ok; negative value otherwise
   */
  public static int translateLine(@NotNull Change change, int line) {
    int result = line;

    Change currentChange = change;
    
    while (currentChange != null) {
      if (line < currentChange.line0) break;
      if (line >= currentChange.line0 + currentChange.deleted) {
        result += currentChange.inserted - currentChange.deleted;
      } else {
        return -1;
      }

      currentChange = currentChange.link;
    }

    return result;
  }
  
  public static class Change {
    // todo remove. Return lists instead.
    /**
     * Previous or next edit command.
     */
    public Change link;
    /** # lines of file 1 changed here.  */
    public final int inserted;
    /** # lines of file 0 changed here.  */
    public final int deleted;
    /** Line number of 1st deleted line.  */
    public final int line0;
    /** Line number of 1st inserted line.  */
    public final int line1;

    /** Cons an additional entry onto the front of an edit script OLD.
     LINE0 and LINE1 are the first affected lines in the two files (origin 0).
     DELETED is the number of lines deleted here from file 0.
     INSERTED is the number of lines inserted here in file 1.

     If DELETED is 0 then LINE0 is the number of the line before
     which the insertion was done; vice versa for INSERTED and LINE1.  */
    protected Change(int line0, int line1, int deleted, int inserted, Change old) {
      this.line0 = line0;
      this.line1 = line1;
      this.inserted = inserted;
      this.deleted = deleted;
      link = old;
      //System.err.println(line0+","+line1+","+inserted+","+deleted);
    }

    @NonNls
    public String toString() {
      return "change[" + "inserted=" + inserted + ", deleted=" + deleted + ", line0=" + line0 + ", line1=" + line1 + "]";
    }

    public ArrayList<Change> toList() {
      ArrayList<Change> result = new ArrayList<Change>();
      Change current = this;
      while (current != null) {
        result.add(current);
        current = current.link;
      }
      return result;
    }
  }

  public static class ChangeBuilder implements LCSBuilder {
    private int myIndex1 = 0;
    private int myIndex2 = 0;
    private Change myFirstChange;
    private Change myLastChange;

    public ChangeBuilder(final int startShift) {
      skip(startShift, startShift);
    }

    @Override
    public void addChange(int first, int second) {
      Change change = new Change(myIndex1, myIndex2, first, second, null);
      if (myLastChange != null) myLastChange.link = change;
      else myFirstChange = change;
      myLastChange = change;
      skip(first, second);
    }

    private void skip(int first, int second) {
      myIndex1 += first;
      myIndex2 += second;
    }

    @Override
    public void addEqual(int length) {
      skip(length, length);
    }

    public Change getFirstChange() {
      return myFirstChange;
    }
  }
}
