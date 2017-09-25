/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.vcs.checkin.HackSearch;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Comparator;

/**
 * @author irengrig
 *         Date: 2/21/11
 *         Time: 12:19 PM
 */
public class HackSearchTest extends TestCase {
  private HackSearch<T, S, Z> mySearch;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySearch = new HackSearch<>(o -> new Z(o.getInt()), o -> new Z(o.getInt()), new ZComparator());
  }

  public void testSimple() {
    final int idx = mySearch.search(Arrays.asList(new S[]{s(1), s(2), s(4), s(10)}), new T(5));
    Assert.assertEquals(3, idx);
  }

  public void testSame() {
    final int idx = mySearch.search(Arrays.asList(new S[]{s(1), s(2), s(4), s(5), s(10)}), new T(5));
    Assert.assertEquals(3, idx);
  }

  public void testBefore() {
    final int idx = mySearch.search(Arrays.asList(new S[]{s(10), s(20), s(40), s(50), s(60)}), new T(5));
    Assert.assertEquals(0, idx);
  }
  public void testFirst() {
    final int idx = mySearch.search(Arrays.asList(new S[]{s(1), s(2), s(4), s(5), s(10)}), new T(1));
    Assert.assertEquals(0, idx);
  }
  public void testLast() {
    final int idx = mySearch.search(Arrays.asList(new S[]{s(1), s(2), s(4), s(5), s(10)}), new T(15));
    Assert.assertEquals(5, idx);
  }

  private S s(int i) {
    return new S(i);
  }

  private static class T {
    private final int myInt;

    protected T(int anInt) {
      myInt = anInt;
    }

    public int getInt() {
      return myInt;
    }
  }

  private static class S extends T {
    private S(int anInt) {
      super(anInt);
    }
  }

  private static class Z extends T {
    private Z(int anInt) {
      super(anInt);
    }
  }

  private static class ZComparator implements Comparator<Z> {
    @Override
    public int compare(Z o1, Z o2) {
      return Integer.compare(o1.getInt(), o2.getInt());
    }
  }
}
