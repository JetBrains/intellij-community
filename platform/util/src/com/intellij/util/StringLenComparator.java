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
package com.intellij.util;

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
