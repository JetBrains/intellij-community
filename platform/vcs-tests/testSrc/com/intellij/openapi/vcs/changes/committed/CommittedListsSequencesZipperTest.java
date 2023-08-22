// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.DefaultRepositoryLocation;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertTrue;

public class CommittedListsSequencesZipperTest {

  @Test
  public void testSimple() {
    long[][] valuesWithCount = {{1, 3}, {2, 2}, {3, 1}, {5, 1}, {7, 2}, {8, 1}, {17, 1}, {18, 1}, {21, 2}};

    check(valuesWithCount, list(1, 2, 7, 8, 18, 21), list(1, 2, 5, 7, 21), list(1, 3, 17));
  }

  @Test
  public void testVar1() {
    long[][] valuesWithCount = {{1, 1}, {2, 1}, {3, 1}, {5, 1}, {6, 1}, {7, 1}, {11, 1}, {17, 1}, {18, 1}, {22, 1}, {111, 1}};

    check(valuesWithCount, list(1, 7, 11, 111), list(2, 6, 18, 22), list(3, 5, 17));
  }

  @Test
  public void testVar2() {
    long[][] valuesWithCount = {{1, 1}, {2, 1}, {3, 1}, {5, 1}, {6, 1}, {7, 2}, {11, 1}, {17, 1}, {18, 1}, {22, 1}, {111, 1}};

    check(valuesWithCount, list(1, 7, 11, 111), list(2, 6, 7, 18, 22), list(3, 5, 17));
  }

  @Test
  public void testVar3() {
    long[][] valuesWithCount = {{1, 2}, {2, 1}, {3, 1}, {5, 1}, {6, 1}, {7, 1}, {11, 1}, {17, 1}, {18, 1}, {22, 1}, {111, 1}};

    check(valuesWithCount, list(1, 7, 11, 111), list(1, 2, 6, 18, 22), list(3, 5, 17));
  }

  @Test
  public void testVar4() {
    long[][] valuesWithCount = {{1, 1}, {2, 1}, {3, 1}, {5, 1}, {6, 1}, {7, 1}, {11, 1}, {17, 1}, {18, 1}, {22, 1}, {111, 3}};

    check(valuesWithCount, list(1, 7, 11, 111), list(2, 6, 18, 22, 111), list(3, 5, 17, 111));
  }

  @Test
  public void testSame() {
    long[][] valuesWithCount = {{1, 3}, {7, 3}, {11, 3}, {111, 3}};

    check(valuesWithCount, list(1, 7, 11, 111), list(1, 7, 11, 111), list(1, 7, 11, 111));
  }

  @SafeVarargs
  private static void check(long[] @NotNull [] expected, List<CommittedChangeList> @NotNull ... lists) {
    CommittedListsSequencesZipper zipper = new CommittedListsSequencesZipper(ConvertorZipper.ourInstance);
    int id = 0;

    for (List<CommittedChangeList> list : lists) {
      zipper.add(new DefaultRepositoryLocation(String.valueOf(id++)), list);
    }

    checkResult(zipper.execute(), expected);
  }

  private static void checkResult(@NotNull List<CommittedChangeList> result, long[] @NotNull ... numbers) {
    final Set<Long> nums = new HashSet<>();
    final Map<Long, Integer> zipped = new HashMap<>();
    for (long[] pair : numbers) {
      assertTrue(pair.length == 2);
      nums.add(pair[0]);
      if (pair[1] != 1) {
        zipped.put(pair[0], (int) pair[1]);
      }
    }

    long previous = -1;
    for (CommittedChangeList list : result) {
      assertTrue("Ordering error: " + list.getNumber(), previous <= list.getNumber());
      assertTrue("Result does not contain: " + list.getNumber(), nums.contains(list.getNumber()));

      final Integer num = zipped.get(list.getNumber());
      if (num != null) {
        assertTrue("Zipped number differs: list#" + list.getNumber() + "; number:" + list.getComment(),
                   String.valueOf(num).equals(list.getComment()));
      } else {
        assertTrue("Zipped number differs: too much for list where 1 must be: " + list.getNumber(), "1".equals(list.getComment()));
      }
    }
  }

  private static class ConvertorZipper implements VcsCommittedListsZipper {
    private final static ConvertorZipper ourInstance = new ConvertorZipper();

    @Override
    @NotNull
    public Pair<List<RepositoryLocationGroup>, List<RepositoryLocation>> groupLocations(@NotNull List<? extends RepositoryLocation> in) {
      RepositoryLocationGroup group = new RepositoryLocationGroup("");

      for (RepositoryLocation location : in) {
        group.add(location);
      }

      return Pair.create(Collections.singletonList(group), Collections.emptyList());
    }

    @Override
    @NotNull
    public CommittedChangeList zip(@NotNull RepositoryLocationGroup group, @NotNull List<? extends CommittedChangeList> lists) {
      return create(lists.get(0).getNumber(), String.valueOf(lists.size()));
    }

    @Override
    public long getNumber(@NotNull CommittedChangeList list) {
      return list.getNumber();
    }
  }

  @NotNull
  private static List<CommittedChangeList> list(long @NotNull ... numbers) {
    List<CommittedChangeList> result = new ArrayList<>(numbers.length);

    for (long number : numbers) {
      result.add(create(number));
    }

    return result;
  }

  @NotNull
  private static CommittedChangeList create(long number) {
    return create(number, "1");
  }

  @NotNull
  private static CommittedChangeList create(long number, @NotNull String comment) {
    return new CommittedChangeListImpl("", comment, "", number, null, Collections.emptyList()) {
      @Override
      public String toString() {
        return getNumber() + " " + getComment();
      }
    };
  }
}
