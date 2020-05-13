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

import com.intellij.openapi.util.text.CharSequenceWithStringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class PathInterner {
  private static final TObjectHashingStrategy<CharSegment[]> HASHING_STRATEGY = new TObjectHashingStrategy<CharSegment[]>() {
    @Override
    public int computeHashCode(CharSegment[] object) {
      return Arrays.hashCode(object);
    }

    @Override
    public boolean equals(CharSegment[] o1, CharSegment[] o2) {
      return Arrays.equals(o1, o2);
    }
  };
  private final OpenTHashSet<CharSegment> myInternMap = new OpenTHashSet<>();

  @Contract("_,true->!null")
  private CharSegment[] internParts(@NotNull CharSequence path, boolean forAddition) {
    int start = 0;
    boolean asBytes = forAddition && IOUtil.isAscii(path);
    List<CharSegment> key = new ArrayList<>();
    SubSegment flyweightKey = new SubSegment();
    while (start < path.length()) {
      flyweightKey.findSubStringUntilNextSeparator(path, start);
      CharSegment interned = myInternMap.get(flyweightKey);
      if (interned == null) {
        if (!forAddition) {
          return null;
        }
        myInternMap.add(interned = flyweightKey.createPersistentCopy(asBytes));
      }
      key.add(interned);
      start += flyweightKey.length();
    }
    return key.toArray(new CharSegment[0]);
  }

  private static class CharSegment {
    private final Object encodedString; // String or byte[]
    private final int hc;

    private CharSegment(@NotNull Object encodedString, int hc) {
      this.encodedString = encodedString;
      this.hc = hc;
    }

    void appendTo(@NotNull StringBuilder sb) {
      if (encodedString instanceof CharSequence) {
        sb.append(encodedString);
        return;
      }

      int oldLen = sb.length();
      sb.setLength(oldLen + length());
      byte[] bytes = (byte[])encodedString;
      for (int i = 0; i < bytes.length; i++) {
        sb.setCharAt(oldLen + i, (char)bytes[i]);
      }
    }

    char charAt(int i) {
      if (encodedString instanceof CharSequence) {
        return ((CharSequence)encodedString).charAt(i);
      }
      return (char)((byte[])encodedString)[i];
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof CharSegment)) return false;

      CharSegment wrapper = (CharSegment)o;

      if (hashCode() != wrapper.hashCode()) return false;
      if (length() != wrapper.length()) return false;

      for (int i = 0; i < length(); i++) {
        if (charAt(i) != wrapper.charAt(i)) {
          return false;
        }
      }

      return true;
    }

    int length() {
      if (encodedString instanceof CharSequence) {
        return ((CharSequence)encodedString).length();
      }
      return (char)((byte[])encodedString).length;
    }

    @Override
    public int hashCode() {
      return hc;
    }

    @Override
    public String toString() {
      return encodedString instanceof String ? (String)encodedString : new String((byte[])encodedString, StandardCharsets.ISO_8859_1);
    }
  }

  private static class SubSegment extends CharSegment {
    private Object encodedString;
    private int start;
    private int end;
    private int computedHc;

    private SubSegment() {
      super("", 0);
    }

    void findSubStringUntilNextSeparator(@NotNull CharSequence s, int start) {
      encodedString = s;
      this.start = start;

      while (start < s.length() && isSeparator(s.charAt(start))) {
        start++;
      }
      while (start < s.length() && !isSeparator(s.charAt(start))) {
        start++;
      }

      end = start;
      computedHc = StringUtil.stringHashCode(s, this.start, end);
    }

    private static boolean isSeparator(char c) {
      return c == '/' || c == '\\' || c == '.' || c == ' ' || c == '_' || c == '$';
    }

    @Override
    char charAt(int i) {
      if (encodedString instanceof CharSequence) {
        return ((CharSequence)encodedString).charAt(start + i);
      }
      return (char)((byte[])encodedString)[start + i];
    }

    @Override
    int length() {
      return end - start;
    }

    @Override
    public int hashCode() {
      return computedHc;
    }

    @NotNull
    CharSegment createPersistentCopy(boolean asBytes) {
      CharSequence string = (CharSequence)encodedString;
      Object newEncodedString;
      if (asBytes) {
        byte[] bytes = new byte[length()];
        for (int i = 0; i < bytes.length; i++) {
          bytes[i] = (byte)string.charAt(i + start);
        }
        newEncodedString = bytes;
      }
      else {
        newEncodedString = string.subSequence(start, end);
      }
      return new CharSegment(newEncodedString, computedHc);
    }

    @Override
    public String toString() {
      return (encodedString instanceof String ? (String)encodedString : new String((byte[])encodedString, StandardCharsets.ISO_8859_1)).substring(start, end);
    }
  }

  private static class SegmentedCharSequence implements CharSequenceWithStringHash {
    private final CharSegment[] myWrappers;
    private transient int hash;

    private SegmentedCharSequence(CharSegment @NotNull [] wrappers) {
      myWrappers = wrappers;
    }

    @Override
    public int length() {
      int length = 0;
      for (CharSegment wrapper : myWrappers) {
        length += wrapper.length();
      }
      return length;
    }

    @Override
    public char charAt(int index) {
      for (CharSegment wrapper : myWrappers) {
        int length = wrapper.length();
        if (index < length) {
          return wrapper.charAt(index);
        }
        index -= length;
      }
      throw new IndexOutOfBoundsException();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return toString().substring(start, end);
    }

    @NotNull
    @Override
    public String toString() {
      StringBuilder b = new StringBuilder(length());
      for (CharSegment wrapper : myWrappers) {
        wrapper.appendTo(b);
      }
      return b.toString();
    }

    // calculate 31**p mod 2**32 by repeated squaring
    private static int pow31(int p) {
      int base = 31;
      int r = 1;
      while (p != 0) {
        if ((p & 1) != 0) {
          r *= base;
        }
        base *= base;
        p >>= 1;
      }
      return r;
    }

    // hashCode of SegmentedString consisting of three CharSegments a,b,c
    // happens to be (a.hc * 31**b.length() + b.hc) * 31**c.length() + c.hc
    @Override
    public int hashCode() {
      int h = hash;
      if (h == 0) {
        h = myWrappers[0].hc;
        for (int i = 1; i < myWrappers.length; i++) {
          CharSegment wrapper = myWrappers[i];
          h = h * pow31(wrapper.length()) + wrapper.hc;
        }
        hash = h;
        return h;
      }
      return h;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof CharSequence)) return false;
      CharSequence other = (CharSequence)obj;
      if (length() != other.length()) return false;
      int i = 0;
      for (CharSegment wrapper : myWrappers) {
        if (wrapper.encodedString instanceof String) {
          if (!CharArrayUtil.regionMatches(other, i, i + wrapper.length(), (String)wrapper.encodedString)) return false;
          i += wrapper.length();
        }
        else {
          byte[] bytes = (byte[])wrapper.encodedString;
          for (byte b : bytes) {
            char c = (char)b;
            if (c != other.charAt(i++)) return false;
          }
        }
      }
      return true;
    }
  }

  public static class PathEnumerator extends Interner<CharSequence> {
    private final TObjectIntHashMap<CharSegment[]> mySeqToIdx = new TObjectIntHashMap<>(HASHING_STRATEGY);
    private final List<CharSequence> myIdxToSeq = new ArrayList<>();
    private final PathInterner myInterner = new PathInterner();

    public PathEnumerator() {
      myIdxToSeq.add(null);
    }

    @NotNull
    public List<CharSequence> getAllPaths() {
      // 0th is reserved
      return myIdxToSeq.subList(1, myIdxToSeq.size());
    }

    public int addPath(@NotNull CharSequence path) {
      CharSegment[] seq = myInterner.internParts(path, true);
      if (!mySeqToIdx.containsKey(seq)) {
        mySeqToIdx.put(seq, myIdxToSeq.size());
        myIdxToSeq.add(new SegmentedCharSequence(seq));
      }
      return mySeqToIdx.get(seq);
    }

    @NotNull
    public CharSequence retrievePath(int idx) {
      try {
        return myIdxToSeq.get(idx);
      }
      catch (IndexOutOfBoundsException e) {
        throw new IllegalArgumentException("Illegal index: " + idx);
      }
    }

    public boolean containsPath(@NotNull CharSequence path) {
      CharSegment[] key = myInterner.internParts(path, false);
      return key != null && mySeqToIdx.containsKey(key);
    }

    @NotNull
    @Override
    public CharSequence intern(@NotNull CharSequence path) {
      return retrievePath(addPath(path));
    }

    @NotNull
    @Override
    public Set<CharSequence> getValues() {
      return new THashSet<>(getAllPaths());
    }

    @Override
    public void clear() {
      myInterner.myInternMap.clear();
      mySeqToIdx.clear();
      myIdxToSeq.clear();
    }
  }
}
