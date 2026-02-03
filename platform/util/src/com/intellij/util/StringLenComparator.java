// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import java.util.Comparator;

public final class StringLenComparator implements Comparator<String> {
  private static final StringLenComparator ourInstance = new StringLenComparator(true);
  private static final StringLenComparator ourDescendingInstance = new StringLenComparator(false);

  private final boolean myAscending;

  public static StringLenComparator getInstance() {
    return ourInstance;
  }

  public static StringLenComparator getDescendingInstance() {
    return ourDescendingInstance;
  }

  private StringLenComparator(final boolean value) {
    myAscending = value;
  }

  @Override
  public int compare(final String o1, final String o2) {
    int k = myAscending ? 1 : -1;
    return (o1.length() == o2.length()) ? 0 : (k * ((o1.length() < o2.length()) ? -1 : 1));
  }
}
