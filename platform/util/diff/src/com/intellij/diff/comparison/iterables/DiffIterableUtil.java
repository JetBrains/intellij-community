// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables;

import com.intellij.diff.comparison.CancellationChecker;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.comparison.TrimUtil;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.util.Range;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.PeekableIteratorWrapper;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DiffIterableUtil {

  private DiffIterableUtil() { }

  @TestOnly
  private static boolean SHOULD_VERIFY_ITERABLE = false;

  /*
   * Compare two integer arrays
   */
  public static @NotNull FairDiffIterable diff(int @NotNull [] data1, int @NotNull [] data2, @NotNull CancellationChecker indicator)
    throws DiffTooBigException {
    indicator.checkCanceled();

    try {
      // TODO: use CancellationChecker inside
      Diff.Change change = Diff.buildChanges(data1, data2);
      return fair(create(change, data1.length, data2.length));
    }
    catch (FilesTooBigForDiffException e) {
      throw new DiffTooBigException();
    }
  }

  /*
   * Compare two arrays, basing on equals() and hashCode() of it's elements
   */
  public static @NotNull <T> FairDiffIterable diff(T @NotNull [] data1, T @NotNull [] data2, @NotNull CancellationChecker indicator)
    throws DiffTooBigException {
    indicator.checkCanceled();

    try {
      // TODO: use CancellationChecker inside
      Diff.Change change = Diff.buildChanges(data1, data2);
      return fair(create(change, data1.length, data2.length));
    }
    catch (FilesTooBigForDiffException e) {
      throw new DiffTooBigException();
    }
  }

  /*
   * Compare two lists, basing on equals() and hashCode() of it's elements
   */
  public static @NotNull <T> FairDiffIterable diff(@NotNull List<? extends T> objects1,
                                          @NotNull List<? extends T> objects2,
                                          @NotNull CancellationChecker indicator)
    throws DiffTooBigException {
    // TODO: compare lists instead of arrays in Diff
    return diff(objects1.toArray(), objects2.toArray(), indicator);
  }

  //
  // Iterable
  //

  public static @NotNull DiffIterable create(@Nullable Diff.Change change, int length1, int length2) {
    DiffChangeDiffIterable iterable = new DiffChangeDiffIterable(change, length1, length2);
    verify(iterable);
    return iterable;
  }

  public static @NotNull DiffIterable createFragments(@NotNull List<? extends DiffFragment> fragments, int length1, int length2) {
    DiffIterable iterable = new DiffFragmentsDiffIterable(fragments, length1, length2);
    verify(iterable);
    return iterable;
  }

  public static @NotNull DiffIterable create(@NotNull List<? extends Range> ranges, int length1, int length2) {
    DiffIterable iterable = new RangesDiffIterable(ranges, length1, length2);
    verify(iterable);
    return iterable;
  }

  public static @NotNull DiffIterable createUnchanged(@NotNull List<? extends Range> ranges, int length1, int length2) {
    DiffIterable invert = invert(create(ranges, length1, length2));
    verify(invert);
    return invert;
  }

  public static @NotNull DiffIterable invert(@NotNull DiffIterable iterable) {
    DiffIterable wrapper = new InvertedDiffIterableWrapper(iterable);
    verify(wrapper);
    return wrapper;
  }

  public static @NotNull FairDiffIterable fair(@NotNull DiffIterable iterable) {
    if (iterable instanceof FairDiffIterable) return (FairDiffIterable)iterable;
    FairDiffIterable wrapper = new FairDiffIterableWrapper(iterable);
    verifyFair(wrapper);
    return wrapper;
  }

  public static @NotNull DiffIterable expandedIterable(@NotNull DiffIterable iterable, int offset1, int offset2, int length1, int length2) {
    assert offset1 + iterable.getLength1() <= length1 &&
           offset2 + iterable.getLength2() <= length2;
    return new ExpandedDiffIterable(iterable, offset1, offset2, length1, length2);
  }

  //
  // Misc
  //

  /**
   * Iterate both changed and unchanged ranges one-by-one.
   */
  public static @NotNull Iterable<Pair<Range, /* isUnchanged */ Boolean>> iterateAll(final @NotNull DiffIterable iterable) {
    return () -> new AllRangesIterator(iterable);
  }

  public static int getRangeDelta(@NotNull Range range) {
    int deleted = range.end1 - range.start1;
    int inserted = range.end2 - range.start2;
    return inserted - deleted;
  }

  //
  // Verification
  //

  @TestOnly
  public static void setVerifyEnabled(boolean value) {
    SHOULD_VERIFY_ITERABLE = value;
  }

  private static boolean isVerifyEnabled() {
    return SHOULD_VERIFY_ITERABLE;
  }

  public static void verify(@NotNull DiffIterable iterable) {
    if (!isVerifyEnabled()) return;

    verify(iterable.iterateChanges());
    verify(iterable.iterateUnchanged());

    verifyFullCover(iterable);
  }

  public static void verifyFair(@NotNull DiffIterable iterable) {
    if (!isVerifyEnabled()) return;

    verify(iterable);

    for (Range range : iterable.iterateUnchanged()) {
      assert range.end1 - range.start1 == range.end2 - range.start2;
    }
  }

  private static void verify(@NotNull Iterable<? extends Range> iterable) {
    for (Range range : iterable) {
      // verify range
      assert range.start1 <= range.end1;
      assert range.start2 <= range.end2;
      assert range.start1 != range.end1 || range.start2 != range.end2;
    }
  }

  private static void verifyFullCover(@NotNull DiffIterable iterable) {
    int last1 = 0;
    int last2 = 0;
    Boolean lastEquals = null;

    for (Pair<Range, Boolean> pair : iterateAll(iterable)) {
      Range range = pair.first;
      Boolean equal = pair.second;

      assert last1 == range.start1;
      assert last2 == range.start2;
      assert !Comparing.equal(lastEquals, equal);

      last1 = range.end1;
      last2 = range.end2;
      lastEquals = equal;
    }

    assert last1 == iterable.getLength1();
    assert last2 == iterable.getLength2();
  }

  //
  // Helpers
  //

  public abstract static class ChangeBuilderBase {
    private final int myLength1;
    private final int myLength2;

    private int myIndex1 = 0;
    private int myIndex2 = 0;

    public ChangeBuilderBase(int length1, int length2) {
      myLength1 = length1;
      myLength2 = length2;
    }

    public int getIndex1() {
      return myIndex1;
    }

    public int getIndex2() {
      return myIndex2;
    }

    public int getLength1() {
      return myLength1;
    }

    public int getLength2() {
      return myLength2;
    }

    public void markEqual(int index1, int index2) {
      markEqual(index1, index2, 1);
    }

    public void markEqual(int index1, int index2, int count) {
      markEqual(index1, index2, index1 + count, index2 + count);
    }

    public void markEqual(int index1, int index2, int end1, int end2) {
      if (index1 == end1 && index2 == end2) return;

      assert myIndex1 <= index1;
      assert myIndex2 <= index2;
      assert index1 <= end1;
      assert index2 <= end2;

      if (myIndex1 != index1 || myIndex2 != index2) {
        addChange(myIndex1, myIndex2, index1, index2);
      }
      myIndex1 = end1;
      myIndex2 = end2;
    }

    protected void doFinish() {
      assert myIndex1 <= myLength1;
      assert myIndex2 <= myLength2;

      if (myLength1 != myIndex1 || myLength2 != myIndex2) {
        addChange(myIndex1, myIndex2, myLength1, myLength2);
        myIndex1 = myLength1;
        myIndex2 = myLength2;
      }
    }

    protected abstract void addChange(int start1, int start2, int end1, int end2);
  }

  public static class ChangeBuilder extends ChangeBuilderBase {
    private final List<Range> myChanges = new ArrayList<>();

    public ChangeBuilder(int length1, int length2) {
      super(length1, length2);
    }

    @Override
    protected void addChange(int start1, int start2, int end1, int end2) {
      myChanges.add(new Range(start1, end1, start2, end2));
    }

    public @NotNull DiffIterable finish() {
      doFinish();
      return create(myChanges, getLength1(), getLength2());
    }
  }

  public static class ExpandChangeBuilder extends ChangeBuilder {
    private final @NotNull List<?> myObjects1;
    private final @NotNull List<?> myObjects2;

    public ExpandChangeBuilder(@NotNull List<?> objects1, @NotNull List<?> objects2) {
      super(objects1.size(), objects2.size());
      myObjects1 = objects1;
      myObjects2 = objects2;
    }

    @Override
    protected void addChange(int start1, int start2, int end1, int end2) {
      Range range = TrimUtil.expand(myObjects1, myObjects2, start1, start2, end1, end2);
      if (!range.isEmpty()) super.addChange(range.start1, range.start2, range.end1, range.end2);
    }
  }

  //
  // Debug
  //

  @SuppressWarnings("unused")
  public static @NotNull <T> List<LineRangeData> extractDataRanges(@NotNull List<? extends T> objects1,
                                                                   @NotNull List<? extends T> objects2,
                                                                   @NotNull DiffIterable iterable) {
    List<LineRangeData> result = new ArrayList<>();

    for (Pair<Range, Boolean> pair : iterateAll(iterable)) {
      Range range = pair.first;
      boolean equals = pair.second;

      List<T> data1 = new ArrayList<>();
      List<T> data2 = new ArrayList<>();

      for (int i = range.start1; i < range.end1; i++) {
        data1.add(objects1.get(i));
      }
      for (int i = range.start2; i < range.end2; i++) {
        data2.add(objects2.get(i));
      }

      result.add(new LineRangeData<>(data1, data2, equals));
    }

    return result;
  }

  public static class LineRangeData<T> {
    public final boolean equals;
    public final @NotNull List<T> objects1;
    public final @NotNull List<T> objects2;

    public LineRangeData(@NotNull List<T> objects1, @NotNull List<T> objects2, boolean equals) {
      this.equals = equals;
      this.objects1 = objects1;
      this.objects2 = objects2;
    }
  }

  private static class AllRangesIterator implements Iterator<Pair<Range, Boolean>> {
    private final @NotNull DiffIterable myIterable;
    private final @NotNull PeekableIteratorWrapper<Range> myChanges;

    private Range myNextUnchanged;

    AllRangesIterator(@NotNull DiffIterable iterable) {
      myChanges = new PeekableIteratorWrapper<>(iterable.changes());
      myIterable = iterable;

      myNextUnchanged = peekNextUnchanged(0, 0);
    }

    private @Nullable Range peekNextUnchanged(int start1, int start2) {
      Range nextChange = myChanges.hasNext() ? myChanges.peek() : null;
      Range range = nextChange != null
                    ? new Range(start1, nextChange.start1, start2, nextChange.start2)
                    : new Range(start1, myIterable.getLength1(), start2, myIterable.getLength2());
      if (range.isEmpty()) return null;
      return range;
    }

    @Override
    public boolean hasNext() {
      return myChanges.hasNext() || myNextUnchanged != null;
    }

    @Override
    public Pair<Range, Boolean> next() {
      if (myNextUnchanged != null) {
        Range result = myNextUnchanged;
        myNextUnchanged = null;
        return Pair.create(result, true);
      }

      Range range = myChanges.next();
      myNextUnchanged = peekNextUnchanged(range.end1, range.end2);
      return Pair.create(range, false);
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
