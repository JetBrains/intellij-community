// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.diff;

import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.util.containers.Enumerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

public final class Diff {
  private static final LoggerRt LOG = LoggerRt.getInstance(Diff.class);

  @Nullable
  public static Change buildChanges(@NotNull CharSequence before, @NotNull CharSequence after) throws FilesTooBigForDiffException {
    return buildChanges(splitLines(before), splitLines(after));
  }

  public static String @NotNull [] splitLines(@NotNull CharSequence s) {
    return s.length() == 0 ? new String[]{""} : LineTokenizer.tokenize(s, false, false);
  }

  @Nullable
  public static <T> Change buildChanges(T @NotNull [] objects1, T @NotNull [] objects2) throws FilesTooBigForDiffException {
    // Old variant of enumerator worked incorrectly with null values.
    // This check is to ensure that the corrected version does not introduce bugs.
    for (T anObjects1 : objects1) {
      assert anObjects1 != null;
    }
    for (T anObjects2 : objects2) {
      assert anObjects2 != null;
    }

    final int startShift = getStartShift(objects1, objects2);
    final int endCut = getEndCut(objects1, objects2, startShift);

    Ref<Change> changeRef = doBuildChangesFast(objects1.length, objects2.length, startShift, endCut);
    if (changeRef != null) return changeRef.get();

    int trimmedLength = objects1.length + objects2.length - 2 * startShift - 2 * endCut;
    Enumerator<T> enumerator = new Enumerator<>(trimmedLength);
    int[] ints1 = enumerator.enumerate(objects1, startShift, endCut);
    int[] ints2 = enumerator.enumerate(objects2, startShift, endCut);
    return doBuildChanges(ints1, ints2, new ChangeBuilder(startShift));
  }

  @Nullable
  public static Change buildChanges(int @NotNull [] array1, int @NotNull [] array2) throws FilesTooBigForDiffException {
    final int startShift = getStartShift(array1, array2);
    final int endCut = getEndCut(array1, array2, startShift);

    Ref<Change> changeRef = doBuildChangesFast(array1.length, array2.length, startShift, endCut);
    if (changeRef != null) return changeRef.get();

    boolean copyArray = startShift != 0 || endCut != 0;
    int[] ints1 = copyArray ? Arrays.copyOfRange(array1, startShift, array1.length - endCut) : array1;
    int[] ints2 = copyArray ? Arrays.copyOfRange(array2, startShift, array2.length - endCut) : array2;
    return doBuildChanges(ints1, ints2, new ChangeBuilder(startShift));
  }

  @Nullable
  private static Ref<Change> doBuildChangesFast(int length1, int length2, int startShift, int endCut) {
    int trimmedLength1 = length1 - startShift - endCut;
    int trimmedLength2 = length2 - startShift - endCut;
    if (trimmedLength1 != 0 && trimmedLength2 != 0) return null;
    Change change = trimmedLength1 != 0 || trimmedLength2 != 0 ?
                    new Change(startShift, startShift, trimmedLength1, trimmedLength2, null) :
                    null;
    return new Ref<>(change);
  }

  private static Change doBuildChanges(int @NotNull [] ints1, int @NotNull [] ints2, @NotNull ChangeBuilder builder)
    throws FilesTooBigForDiffException {
    Reindexer reindexer = new Reindexer(); // discard unique elements, that have no chance to be matched
    int[][] discarded = reindexer.discardUnique(ints1, ints2);

    if (discarded[0].length == 0 && discarded[1].length == 0) {
      // assert trimmedLength > 0
      builder.addChange(ints1.length, ints2.length);
      return builder.getFirstChange();
    }

    BitSet[] changes;
    if (DiffConfig.USE_PATIENCE_ALG) {
      PatienceIntLCS patienceIntLCS = new PatienceIntLCS(discarded[0], discarded[1]);
      patienceIntLCS.execute();
      changes = patienceIntLCS.getChanges();
    }
    else {
      try {
        MyersLCS intLCS = new MyersLCS(discarded[0], discarded[1]);
        intLCS.executeWithThreshold();
        changes = intLCS.getChanges();
      }
      catch (FilesTooBigForDiffException e) {
        PatienceIntLCS patienceIntLCS = new PatienceIntLCS(discarded[0], discarded[1]);
        patienceIntLCS.execute(true);
        changes = patienceIntLCS.getChanges();
        LOG.info("Successful fallback to patience diff");
      }
    }

    reindexer.reindex(changes, builder);
    return builder.getFirstChange();
  }

  private static <T> int getStartShift(final T @NotNull [] o1, final T @NotNull [] o2) {
    final int size = Math.min(o1.length, o2.length);
    int idx = 0;
    for (int i = 0; i < size; i++) {
      if (!o1[i].equals(o2[i])) break;
      ++idx;
    }
    return idx;
  }

  private static <T> int getEndCut(final T @NotNull [] o1, final T @NotNull [] o2, int startShift) {
    final int size = Math.min(o1.length, o2.length) - startShift;
    int idx = 0;

    for (int i = 0; i < size; i++) {
      if (!o1[o1.length - i - 1].equals(o2[o2.length - i - 1])) break;
      ++idx;
    }
    return idx;
  }

  private static int getStartShift(final int @NotNull [] o1, final int @NotNull [] o2) {
    final int size = Math.min(o1.length, o2.length);
    int idx = 0;
    for (int i = 0; i < size; i++) {
      if (o1[i] != o2[i]) break;
      ++idx;
    }
    return idx;
  }

  private static int getEndCut(final int @NotNull [] o1, final int @NotNull [] o2, final int startShift) {
    final int size = Math.min(o1.length, o2.length) - startShift;
    int idx = 0;

    for (int i = 0; i < size; i++) {
      if (o1[o1.length - i - 1] != o2[o2.length - i - 1]) break;
      ++idx;
    }
    return idx;
  }

  public static int translateLine(@NotNull CharSequence before, @NotNull CharSequence after, int line, boolean approximate)
    throws FilesTooBigForDiffException {
    String[] strings1 = LineTokenizer.tokenize(before, false);
    String[] strings2 = LineTokenizer.tokenize(after, false);
    if (approximate) {
      strings1 = trim(strings1);
      strings2 = trim(strings2);
    }
    Change change = buildChanges(strings1, strings2);
    return translateLine(change, line, approximate);
  }

  private static String @NotNull [] trim(String @NotNull [] lines) {
    String[] result = new String[lines.length];
    for (int i = 0; i < lines.length; i++) {
      result[i] = lines[i].trim();
    }
    return result;
  }

  /**
   * Tries to translate given line that pointed to the text before change to the line that points to the same text after the change.
   *
   * @param change    target change
   * @param line      target line before change
   * @return          translated line if the processing is ok; negative value otherwise
   */
  public static int translateLine(@Nullable Change change, int line) {
    return translateLine(change, line, false);
  }

  public static int translateLine(@Nullable Change change, int line, boolean approximate) {
    int result = line;

    Change currentChange = change;
    while (currentChange != null) {
      if (line < currentChange.line0) break;
      if (line >= currentChange.line0 + currentChange.deleted) {
        result += currentChange.inserted - currentChange.deleted;
      } else {
        return approximate ? currentChange.line1 : -1;
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
    public Change(int line0, int line1, int deleted, int inserted, @Nullable Change old) {
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
      ArrayList<Change> result = new ArrayList<>();
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

  public static @Nullable CharSequence linesDiff(
    @NotNull CharSequence @NotNull [] lines1,
    @NotNull CharSequence @NotNull [] lines2
  ) throws FilesTooBigForDiffException {
    Change ch = buildChanges(lines1, lines2);
    if (ch == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    while (ch != null) {
      if (sb.length() != 0) {
        sb.append("====================").append("\n");
      }
      for (int i = ch.line0; i < ch.line0 + ch.deleted; i++) {
        sb.append('-').append(lines1[i]).append('\n');
      }
      for (int i = ch.line1; i < ch.line1 + ch.inserted; i++) {
        sb.append('+').append(lines2[i]).append('\n');
      }
      ch = ch.link;
    }
    return sb.toString();
  }
}
