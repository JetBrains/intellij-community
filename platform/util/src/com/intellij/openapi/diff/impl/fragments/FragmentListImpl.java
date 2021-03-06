// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.fragments;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

public final class FragmentListImpl implements FragmentList {
  private static final Logger LOG = Logger.getInstance(FragmentList.class);
  private final ArrayList<Fragment> myFragments;

  private <T extends Fragment> FragmentListImpl(ArrayList<T> sortedFragments) {
    myFragments = (ArrayList<Fragment>)sortedFragments;
  }

  private void init() {
    myFragments.sort(FRAGMENT_COMPARATOR);
    myFragments.trimToSize();
  }

  public static <T extends Fragment> FragmentList fromList(ArrayList<T> fragments) {
    FragmentListImpl fragmentList = new FragmentListImpl(fragments);
    fragmentList.init();
    return fragmentList;
  }

  @Override
  public FragmentList shift(TextRange rangeShift1, TextRange rangeShift2,
                            int startLine1, int startLine2) {
    return new FragmentListImpl(shift(myFragments, rangeShift1, rangeShift2, startLine1, startLine2));
  }

  @Override
  public boolean isEmpty() {
    return myFragments.isEmpty();
  }

  @Override
  public Iterator<Fragment> iterator() {
    return myFragments.iterator();
  }

  @Override
  public Fragment getFragmentAt(int offset, FragmentSide side, Condition<? super Fragment> condition) {
    for (Iterator<Fragment> iterator = iterator(); iterator.hasNext();) {
      Fragment fragment = iterator.next();
      TextRange range = fragment.getRange(side);
      if (range.getStartOffset() <= offset &&
          range.getEndOffset() > offset &&
          condition.value(fragment)) return fragment.getSubfragmentAt(offset, side, condition);
    }
    return null;
  }

  public static ArrayList<Fragment> shift(ArrayList<? extends Fragment> fragments, TextRange rangeShift1, TextRange rangeShift2,
                                          int startLine1, int startLine2) {
    ArrayList<Fragment> newFragments = new ArrayList<>(fragments.size());
    for (Fragment fragment : fragments) {
      newFragments.add(fragment.shift(rangeShift1, rangeShift2, startLine1, startLine2));
    }
    return newFragments;
  }

  private static final Comparator<Fragment> FRAGMENT_COMPARATOR = (fragment1, fragment2) -> {
    int result = compareBySide(fragment1, fragment2, FragmentSide.SIDE1);
    int check = compareBySide(fragment1, fragment2, FragmentSide.SIDE2);
    LOG.assertTrue(result == 0 || check == 0 || sign(result) == sign(check));
    return result;
  };

  private static int sign(int n) {
    if (n == 0)
      return 0;
    else
      return n > 0 ? 1 : -1;
  }

  private static int compareBySide(Fragment fragment1, Fragment fragment2, FragmentSide side) {
    int start1 = fragment1.getRange(side).getStartOffset();
    int start2 = fragment2.getRange(side).getStartOffset();
    return start1 - start2;
  }
}
