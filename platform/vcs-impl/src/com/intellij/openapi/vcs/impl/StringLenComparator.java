package com.intellij.openapi.vcs.impl;

import java.util.Comparator;

public class StringLenComparator implements Comparator<String> {
  private final static StringLenComparator ourInstance = new StringLenComparator(true);
  private final static StringLenComparator ourDescendingInstance = new StringLenComparator(false);

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

  public int compare(final String o1, final String o2) {
    final int revertor = myAscending ? 1 : -1;
    return (o1.length() == o2.length()) ? 0 : (revertor * ((o1.length() < o2.length()) ? -1 : 1));
  }
}
