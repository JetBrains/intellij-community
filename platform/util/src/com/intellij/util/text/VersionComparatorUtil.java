/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Leonid Shalupov
 *
 * This versions comparator is much smarter than StringUtil.compareVersionNumbers
 * E.g: is used for TeamCity plugins and Ruby gems versions
 */
public class VersionComparatorUtil {
  private static final Pattern WORDS_SPLITTER = Pattern.compile("\\d+|[^\\d]+");
  private static final VersionTokenType[] VALUES = VersionTokenType.values();

  public static final Comparator<String> COMPARATOR = new Comparator<String>() {
    @Override
    public int compare(String s1, String s2) {
      return VersionComparatorUtil.compare(s1, s2);
    }
  };

  private VersionComparatorUtil() {
  }

  public static String max(final String v1, final String v2) {
    return compare(v1, v2) > 0 ? v1 : v2;
  }

  public static String min(final String v1, final String v2) {
    return compare(v1, v2) < 0 ? v1 : v2;
  }

  public enum VersionTokenType {
    SNAP(10), SNAPSHOT(10),
    M(20),
    EAP(25), PRE(25), PREVIEW(25),
    ALPHA(30), A(30),
    BETA(40), BETTA(40), B(40),
    RC(50),
    _WS(60),
    SP(70),
    REL(80), RELEASE(80), R(80), FINAL(80),
    _WORD(90),
    _DIGITS(100),
    BUNDLED(666);

    private final int myPriority;

    VersionTokenType(final int priority) {
      myPriority = priority;
    }

    @NotNull
    public static VersionTokenType lookup(String str) {
      if (str == null) {
        return _WS;
      }

      str = str.trim();
      if (str.isEmpty()) {
        return _WS;
      }

      for (VersionTokenType token : VALUES) {
        final String name = token.name();
        if (name.charAt(0) != '_' && name.equalsIgnoreCase(str)) {
          return token;
        }
      }

      if (str.matches("0+")) {
        return _WS;
      }

      if (str.matches("\\d+")) {
        return _DIGITS;
      }

      return _WORD;
    }

    public int getPriority() {
      return myPriority;
    }
  }

  static List<String> splitVersionString(final String ver) {
    StringTokenizer st = new StringTokenizer(ver.trim(), "()._-;:/, +~");
    List<String> result = new ArrayList<String>();

    while (st.hasMoreTokens()) {
      final Matcher matcher = WORDS_SPLITTER.matcher(st.nextToken());

      while (matcher.find()) {
        result.add(matcher.group());
      }
    }

    return result;
  }

  /**
   * Compare two version strings. See TeamCity documentation on requirements comparison
   * for formal description.
   *
   * Examples: 1.0rc1 < 1.0release, 1.0 < 1.0.1, 1.1 > 1.02
   * @return 0 if ver1 equals ver2, positive value if ver1 > ver2, negative value if ver1 < ver2
   */
  public static int compare(String ver1, String ver2) {
    // todo duplicates com.intellij.openapi.util.text.StringUtil.compareVersionNumbers()
    // todo please refactor next time you make changes here
    if (ver1 == null) {
      return (ver2 == null) ? 0 : -1;
    } else if (ver2 == null) {
      return 1;
    }

    ver1 = ver1.toLowerCase();
    ver2 = ver2.toLowerCase();

    final List<String> s1 = splitVersionString(ver1);
    final List<String> s2 = splitVersionString(ver2);

    padWithNulls(s1, s2);

    int res = 0;
    for (int i = 0; i < s1.size(); i++) {
      final String e1 = s1.get(i);
      final String e2 = s2.get(i);

      final VersionTokenType t1 = VersionTokenType.lookup(e1);
      final VersionTokenType t2 = VersionTokenType.lookup(e2);

      if (!t1.equals(t2)) {
        res = comparePriorities(t1, t2);
      } else if (t1 == VersionTokenType._WORD) {
        res = e1.compareTo(e2);
      } else if (t1 == VersionTokenType._DIGITS) {
        res = compareNumbers(e1, e2);
      }

      if (res != 0) {
        return res;
      }
    }

    return 0;
  }

  private static int comparePriorities(VersionTokenType t1, VersionTokenType t2) {
    return Integer.signum(t1.getPriority() - t2.getPriority());
  }

  private static int compareNumbers(String n1, String n2) {
    // trim leading zeros
    while(!n1.isEmpty() && !n2.isEmpty() && n1.charAt(0) == '0' && n2.charAt(0) == '0') {
      n1 = n1.substring(1);
      n2 = n2.substring(1);
    }

    // starts with zero => less
    if (!n1.isEmpty() && n1.charAt(0) == '0') {
      return -1;
    } else if (!n2.isEmpty() && n2.charAt(0) == '0') {
      return 1;
    }

    // compare as numbers
    final int n1len = n1.length();
    final int n2len = n2.length();

    if (n1len > n2len) {
      n2 = StringUtil.repeatSymbol('0', n1len - n2len) + n2;
    } else if (n2len > n1len) {
      n1 = StringUtil.repeatSymbol('0', n2len - n1len) + n1;
    }

    return n1.compareTo(n2);
  }

  private static void padWithNulls(final Collection<String> s1, final Collection<String> s2) {
    if (s1.size() != s2.size()) {
      while (s1.size() < s2.size()) {
        s1.add(null);
      }
      while (s1.size() > s2.size()) {
        s2.add(null);
      }
    }
  }
}

