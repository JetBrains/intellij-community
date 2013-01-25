/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author irengrig
 */
public abstract class AbstractHash {
  @Nullable
  public static AbstractHash createStrict(final String hash) {
    return createImpl(hash, true);
  }

  @NotNull
  public static AbstractHash create(final String hash) {
    return createImpl(hash, false);
  }

  private static AbstractHash createImpl(final String hash, final boolean strict) {
    final String trimmed = hash.trim();
    final int len = trimmed.length();
    try {
      if (len <= 8 && trimmed.charAt(0) != '0') {
        return new One(trimmed);
      } else {
        return new Many(trimmed);
      }
    } catch (NumberFormatException e) {
      if (strict) return null;
      return new StringPresentation(trimmed);
    }
  }

  public abstract String getString();

  @Override
  public String toString() {
    return getString();
  }

  private static class One extends AbstractHash {
    private long myLong;

    private One(final String shortForm) {
      assert shortForm.length() <= 8;
      myLong = Long.parseLong(shortForm, 16);
    }

    @Override
    public String getString() {
      return Long.toHexString(myLong);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      One one = (One)o;

      if (myLong != one.myLong) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return (int)(myLong ^ (myLong >>> 32));
    }
  }

  private static class Many extends AbstractHash {
    private final long[] myData;

    private Many(final String shortForm) {
      int nullsSize = 0;
      for (; nullsSize < shortForm.length(); nullsSize++) {
        if (shortForm.charAt(nullsSize) != '0') break;
      }
      final String withoutNulls = shortForm.substring(nullsSize);
      final int length = withoutNulls.length();
      final int size = (length >> 3) + 1 + nullsSize;
      myData = new long[size];
      for (int i = 0; i < nullsSize; i++) {
        myData[i] = 0;
      }
      for (int i = 0; i < (size - nullsSize); i++) {
        final int idx = i << 3;
        final int end = Math.min(idx + 8, length);
        myData[i + nullsSize] = Long.parseLong(withoutNulls.substring(idx, end), 16);
      }
    }

    // todo?
    @Override
    public String getString() {
      final StringBuilder sb = new StringBuilder(myData.length << 3);
      for (long l : myData) {
        sb.append(Long.toHexString(l));
      }
      return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Many many = (Many)o;

      if (!Arrays.equals(myData, many.myData)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(myData);
    }
  }

  private static class StringPresentation extends AbstractHash {
    private final String myVal;

    public StringPresentation(String val) {
      myVal = val;
    }

    @Override
    public String getString() {
      return myVal;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      StringPresentation that = (StringPresentation)o;

      if (myVal != null ? !myVal.equals(that.myVal) : that.myVal != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myVal != null ? myVal.hashCode() : 0;
    }
  }

  public static boolean hashesEqual(@NotNull final AbstractHash hash1, @NotNull final AbstractHash hash2) {
    if (hash1.equals(hash2)) return true;
    final String s1 = hash1.getString();
    final String s2 = hash2.getString();
    if (s1.startsWith(s2) || s2.startsWith(s1)) return true;
    return false;
  }
}
