/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.containers;

import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class PathInterner {
  private static final TObjectHashingStrategy<SubstringWrapper[]> HASHING_STRATEGY = new TObjectHashingStrategy<SubstringWrapper[]>() {
    @Override
    public int computeHashCode(SubstringWrapper[] object) {
      return Arrays.hashCode(object);
    }

    @Override
    public boolean equals(SubstringWrapper[] o1, SubstringWrapper[] o2) {
      return Arrays.equals(o1, o2);
    }
  };
  private final OpenTHashSet<SubstringWrapper> myInternMap = new OpenTHashSet<SubstringWrapper>();

  @Nullable
  protected SubstringWrapper[] internParts(String path, boolean forAddition) {
    int start = 0;
    List<SubstringWrapper> key = new ArrayList<SubstringWrapper>();
    SubstringWrapper flyweightKey = new SubstringWrapper();
    while (start < path.length()) {
      flyweightKey.findSubStringUntilNextSeparator(path, start);
      SubstringWrapper interned = myInternMap.get(flyweightKey);
      if (interned == null) {
        if (!forAddition) {
          return null;
        }
        myInternMap.add(interned = flyweightKey.createPersistentCopy());
      }
      key.add(interned);
      start += flyweightKey.len;
    }
    return key.toArray(new SubstringWrapper[key.size()]);
  }

  private static String restorePath(SubstringWrapper[] seq) {
    StringBuilder sb = new StringBuilder();
    for (SubstringWrapper wrapper : seq) {
      sb.append(wrapper.s);
    }
    return sb.toString();
  }

  private static class SubstringWrapper {
    private String s;
    private int start;
    private int len;
    private int hc;

    void findSubStringUntilNextSeparator(String s, int start) {
      this.s = s;
      this.start = start;
      hc = 0;

      while (start < s.length() && isSeparator(s.charAt(start))) {
        hc = hc * 31 + s.charAt(start);
        start++;
      }
      while (start < s.length() && !isSeparator(s.charAt(start))) {
        hc = hc * 31 + s.charAt(start);
        start++;
      }
      this.len = start - this.start;
    }

    private static boolean isSeparator(char c) {
      return c == '/' || c == '\\' || c == '.' || c == ' ' || c == '_' || c == '$';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SubstringWrapper)) return false;

      SubstringWrapper wrapper = (SubstringWrapper)o;

      if (hc != wrapper.hc) return false;
      if (len != wrapper.len) return false;
      if (s == wrapper.s && start == wrapper.start) return true;

      for (int i = 0; i < len; i++) {
        if (s.charAt(i + start) != wrapper.s.charAt(i + wrapper.start)) {
          return false;
        }
      }

      return true;
    }

    @Override
    public int hashCode() {
      return hc;
    }

    SubstringWrapper createPersistentCopy() {
      SubstringWrapper wrapper = new SubstringWrapper();
      wrapper.s = new String(s.substring(start, start + len));
      wrapper.start = 0;
      wrapper.len = len;
      wrapper.hc = hc;
      return wrapper;
    }
  }

  public static class PathEnumerator {
    private final TObjectIntHashMap<SubstringWrapper[]> mySeqToIdx = new TObjectIntHashMap<SubstringWrapper[]>(
      PathInterner.HASHING_STRATEGY);
    private final List<SubstringWrapper[]> myIdxToSeq = new ArrayList<SubstringWrapper[]>();
    private final PathInterner myInterner = new PathInterner();

    public PathEnumerator() {
      myIdxToSeq.add(null);
    }

    public int addPath(String path) {
      PathInterner.SubstringWrapper[] seq = myInterner.internParts(path, true);
      if (!mySeqToIdx.containsKey(seq)) {
        mySeqToIdx.put(seq, myIdxToSeq.size());
        myIdxToSeq.add(seq);
      }
      return mySeqToIdx.get(seq);
    }

    public String retrievePath(int idx) {
      try {
        return PathInterner.restorePath(myIdxToSeq.get(idx));
      }
      catch (IndexOutOfBoundsException e) {
        throw new IllegalArgumentException();
      }
    }

    public boolean containsPath(String path) {
      PathInterner.SubstringWrapper[] key = myInterner.internParts(path, false);
      return key != null && mySeqToIdx.containsKey(key);
    }
  }

  public static class PathMap<T> {
    private final THashMap<SubstringWrapper[], T> myMap = new THashMap<SubstringWrapper[], T>(PathInterner.HASHING_STRATEGY);
    private final PathInterner myInterner = new PathInterner();

    @Nullable
    public T get(@NotNull String path) {
      PathInterner.SubstringWrapper[] seq = myInterner.internParts(path, false);
      return seq == null ? null : myMap.get(seq);
    }

    public void put(@NotNull String path, @NotNull T value) {
      myMap.put(myInterner.internParts(path, true), value);
    }

  }

}
