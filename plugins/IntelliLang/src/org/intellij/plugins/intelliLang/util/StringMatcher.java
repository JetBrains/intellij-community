/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.intelliLang.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.StringPattern;
import com.intellij.util.Function;
import com.intellij.util.containers.WeakHashMap;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;

/**
 * Simple abstraction of a String matcher that can be based on simple and vastly more
 * efficient String comparisons than doing a full pattern match.
 */
public abstract class StringMatcher<T> {
  protected final T myTarget;

  public static final StringMatcher NONE = new Any("<none>", false);
  public static final StringMatcher ANY = new Any("", true);
  public static final StringMatcher ANY_PATTERN = new Any(".*", true);

  protected StringMatcher(T target) {
    myTarget = target;
  }

  private abstract static class Simple extends StringMatcher<String> {
    Simple(String target) {
      super(target);
    }

    public String getPattern() {
      return myTarget;
    }
  }

  private static final class Pattern extends StringMatcher<java.util.regex.Pattern> {
    Pattern(String target) {
      super(java.util.regex.Pattern.compile(target));
    }

    public boolean matches(String what) {
      return myTarget.matcher(StringPattern.newBombedCharSequence(what)).matches();
    }

    public String getPattern() {
      return myTarget.pattern();
    }
  }

  private final static class Equals extends Simple {
    Equals(String target) {
      super(target);
    }

    public boolean matches(String what) {
      return myTarget.equals(what);
    }
  }

  private final static class StartsWith extends Simple {
    StartsWith(String target) {
      super(target);
    }

    public boolean matches(String what) {
      return what.startsWith(myTarget);
    }

    public String getPattern() {
      return super.getPattern() + ".*";
    }
  }

  private final static class EndsWith extends Simple {
    EndsWith(String target) {
      super(target);
    }

    public boolean matches(String what) {
      return what.endsWith(myTarget);
    }

    public String getPattern() {
      return ".*" + super.getPattern();
    }
  }

  private final static class Contains extends Simple {
    Contains(String target) {
      super(target);
    }

    public boolean matches(String what) {
      return what.contains(myTarget);
    }

    public String getPattern() {
      return ".*" + super.getPattern() + ".*";
    }
  }

  private static final class Any extends Simple {
    private final boolean myMatches;

    Any(String target, boolean matches) {
      super(target);
      myMatches = matches;
    }

    public boolean matches(String what) {
      return myMatches;
    }
  }

  private static final class IgnoreCase extends StringMatcher<StringMatcher> {
    IgnoreCase(StringMatcher target) {
      super(target);
    }

    public boolean matches(String what) {
      return myTarget.matches(what.toLowerCase());
    }

    public String getPattern() {
      return "(?i)" + myTarget.getPattern();
    }
  }

  private static final class Cache extends StringMatcher<StringMatcher> {
    private final WeakHashMap<String, Boolean> myCache = new WeakHashMap<>();

    Cache(StringMatcher target) {
      super(target);
    }

    public String getPattern() {
      return myTarget.getPattern();
    }

    public synchronized boolean matches(String what) {
      final Boolean o = myCache.get(what);
      if (o != null) {
        return o;
      }
      final boolean b = myTarget.matches(what);
      myCache.put(what, b);
      return b;
    }
  }

  public static final class MatcherSet extends StringMatcher<Set<StringMatcher>> {
    private final String myPattern;

    protected MatcherSet(Set<StringMatcher> target) {
      super(target);
      myPattern = StringUtil.join(target, s -> s.getPattern(), "|");
    }

    public static StringMatcher create(Set<StringMatcher> matchers) {
      final MatcherSet m = new MatcherSet(matchers);
      return matchers.size() > 3 ? new Cache(m) : m;
    }

    public boolean matches(String what) {
      for (StringMatcher matcher : myTarget) {
        if (matcher.matches(what)) {
          return true;
        }
      }
      return false;
    }

    public String getPattern() {
      return myPattern;
    }
  }

  public abstract boolean matches(String what);

  public abstract String getPattern();

  public static StringMatcher create(String target) {
    if (target.length() == 0) return ANY;
    if (target.equals(".*")) return ANY_PATTERN;
    if (target.equals(NONE.getPattern())) return NONE;

    final List<String> branches = StringUtil.split(target,"|");
    final Set<StringMatcher> matchers = new LinkedHashSet<>();

    for (String branch : branches) {
      boolean startsWith = false;
      boolean endsWith = false;
      boolean ignoreCase = false;

      // this assumes the regex is syntactically correct
      if (branch.startsWith("(?i)")) {
        ignoreCase = true;
        branch = branch.substring(2).toLowerCase();
      }
      if (branch.endsWith(".*")) {
        startsWith = true;
        branch = branch.substring(0, branch.length() - 2);
      }
      if (branch.startsWith(".*")) {
        endsWith = true;
        branch = branch.substring(2);
      }

      final boolean m = analyseBranch(branch);
      if (!m) {
        try {
          return new Cache(new Pattern(target));
        }
        catch (Exception e) {
          return new Any(target, false);
        }
      }

      final StringMatcher matcher;
      if (startsWith && endsWith) {
        matcher = new Contains(branch);
      }
      else if (startsWith) {
        matcher = new StartsWith(branch);
      }
      else if (endsWith) {
        matcher = new EndsWith(branch);
      }
      else {
        matcher = new Equals(branch);
      }

      matchers.add(ignoreCase ? new IgnoreCase(matcher) : matcher);
    }

    return matchers.size() == 1 ? matchers.iterator().next() : MatcherSet.create(matchers);
  }

  private static boolean analyseBranch(String target) {
    for (int i = 0; i < target.length(); i++) {
      final char c = target.charAt(i);
      if (c != '_' && c != '-' && !Character.isLetterOrDigit(c)) {
        return false;
      }
    }
    return true;
  }

  public int hashCode() {
    return myTarget.hashCode();
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean equals(Object obj) {
    if (obj == null) return false;
    if (obj.getClass() != getClass()) return false;
    return ((StringMatcher)obj).myTarget.equals(myTarget);
  }
}
