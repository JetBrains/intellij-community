/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hsz Jakub Chrzanowski <jakub@hsz.mobi>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.intellij.openapi.vcs.changes.ignore.cache;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileConstants;
import com.intellij.openapi.vcs.changes.ignore.lang.Syntax;
import com.intellij.openapi.vcs.changes.ignore.psi.IgnoreEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Component that prepares patterns for glob/regex statements and cache them.
 */
public class PatternCache implements Disposable {
  /**
   * Cache map that holds processed regex statements to the glob rules.
   */
  private final ConcurrentMap<String, String> GLOBS_CACHE = new ConcurrentHashMap<>();

  /**
   * Cache map that holds compiled regex.
   */
  private final ConcurrentMap<String, Pattern> PATTERNS_CACHE = new ConcurrentHashMap<>();

  public PatternCache(@NotNull Project project) {
    Disposer.register(project, this);
  }

  /**
   * Creates regex {@link Pattern} using glob rule.
   *
   * @param rule   rule value
   * @param syntax rule syntax
   * @return regex {@link Pattern}
   */
  public @Nullable Pattern createPattern(@NotNull String rule, @NotNull Syntax syntax) {
    return createPattern(rule, syntax, false);
  }

  /**
   * Creates regex {@link Pattern} using {@link IgnoreEntry}.
   *
   * @param entry {@link IgnoreEntry}
   * @return regex {@link Pattern}
   */
  public @Nullable Pattern createPattern(@NotNull IgnoreEntry entry) {
    return createPattern(entry, false);
  }

  /**
   * Creates regex {@link Pattern} using {@link IgnoreEntry}.
   *
   * @param entry          {@link IgnoreEntry}
   * @param acceptChildren Matches directory children
   * @return regex {@link Pattern}
   */
  public @Nullable Pattern createPattern(@NotNull IgnoreEntry entry, boolean acceptChildren) {
    return createPattern(entry.getValue(), entry.getSyntax(), acceptChildren);
  }

  /**
   * Creates regex {@link Pattern} using glob rule.
   *
   * @param rule           rule value
   * @param syntax         rule syntax
   * @param acceptChildren Matches directory children
   * @return regex {@link Pattern}
   */
  public @Nullable Pattern createPattern(@NotNull String rule, @NotNull Syntax syntax, boolean acceptChildren) {
    String regex = getRegex(rule, syntax, acceptChildren);
    return getOrCreatePattern(regex);
  }

  /**
   * Returns regex string basing on the rule and provided syntax.
   *
   * @param rule           rule value
   * @param syntax         rule syntax
   * @param acceptChildren Matches directory children
   * @return regex string
   */
  public @NotNull String getRegex(@NotNull String rule, @NotNull Syntax syntax, boolean acceptChildren) {
    return syntax.equals(Syntax.GLOB) ? createRegex(rule, acceptChildren) : rule;
  }

  /**
   * Converts regex string to {@link Pattern} with caching.
   *
   * @param regex regex to convert
   * @return {@link Pattern} instance or null if invalid
   */
  public @Nullable Pattern getOrCreatePattern(@NotNull String regex) {
    try {
      if (!PATTERNS_CACHE.containsKey(regex)) {
        PATTERNS_CACHE.put(regex, Pattern.compile(regex));
      }
      return PATTERNS_CACHE.get(regex);
    }
    catch (PatternSyntaxException e) {
      return null;
    }
  }

  public @Nullable Pattern getPattern(@NotNull String regex) {
    return PATTERNS_CACHE.get(regex);
  }

  /**
   * Creates regex {@link String} using glob rule.
   *
   * @param glob           rule
   * @param acceptChildren Matches directory children
   * @return regex {@link String}
   */
  private @NotNull String createRegex(@NotNull String glob, boolean acceptChildren) {
    glob = glob.trim();
    String cached = GLOBS_CACHE.get(glob);
    if (cached != null) {
      return cached;
    }

    StringBuilder sb = new StringBuilder("^");
    boolean escape = false, star = false, doubleStar = false, bracket = false;
    int beginIndex = 0;

    if (StringUtil.startsWith(glob, IgnoreFileConstants.DOUBLESTAR)) {
      sb.append("(?:[^/]*?/)*");
      beginIndex = 2;
      doubleStar = true;
    }
    else if (StringUtil.startsWith(glob, "*/")) {
      sb.append("[^/]*");
      beginIndex = 1;
      star = true;
    }
    else if (StringUtil.equals(IgnoreFileConstants.STAR, glob)) {
      sb.append(".*");
    }
    else if (StringUtil.startsWithChar(glob, '*')) {
      sb.append(".*?");
    }
    else if (StringUtil.startsWithChar(glob, '/')) {
      beginIndex = 1;
    }
    else {
      int slashes = StringUtil.countChars(glob, '/');
      if (slashes == 0 || (slashes == 1 && StringUtil.endsWithChar(glob, '/'))) {
        sb.append("(?:[^/]*?/)*");
      }
    }

    char[] chars = glob.substring(beginIndex).toCharArray();
    for (char ch : chars) {
      if (bracket && ch != ']') {
        sb.append(ch);
        continue;
      }
      else if (doubleStar) {
        doubleStar = false;
        if (ch == '/') {
          sb.append("(?:[^/]*/)*?");
          continue;
        }
        else {
          sb.append("[^/]*?");
        }
      }

      if (ch == '*') {
        if (escape) {
          sb.append("\\*");
          escape = false;
          star = false;
        }
        else if (star) {
          char prev = sb.length() > 0 ? sb.charAt(sb.length() - 1) : '\0';
          if (prev == '\0' || prev == '^' || prev == '/') {
            doubleStar = true;
          }
          else {
            sb.append("[^/]*?");
          }
          star = false;
        }
        else {
          star = true;
        }
        continue;
      }
      else if (star) {
        sb.append("[^/]*?");
        star = false;
      }

      switch (ch) {
        case '\\' -> {
          if (escape) {
            sb.append("\\\\");
            escape = false;
          }
          else {
            escape = true;
          }
        }
        case '?' -> {
          if (escape) {
            sb.append("\\?");
            escape = false;
          }
          else {
            sb.append('.');
          }
        }
        case '[' -> {
          if (escape) {
            sb.append('\\');
            escape = false;
          }
          else {
            bracket = true;
          }
          sb.append(ch);
        }
        case ']' -> {
          if (!bracket) {
            sb.append('\\');
          }
          sb.append(ch);
          bracket = false;
          escape = false;
        }
        case '.', '(', ')', '+', '|', '^', '$', '@', '%' -> {
          sb.append('\\');
          sb.append(ch);
          escape = false;
        }
        default -> {
          escape = false;
          sb.append(ch);
        }
      }
    }

    if (star || doubleStar) {
      if (StringUtil.endsWithChar(sb, '/')) {
        sb.append(acceptChildren ? ".+" : "[^/]+/?");
      }
      else {
        sb.append("[^/]*/?");
      }
    }
    else {
      if (StringUtil.endsWithChar(sb, '/')) {
        if (acceptChildren) {
          sb.append("[^/]*");
        }
      }
      else {
        sb.append(acceptChildren ? "(?:/.*)?" : "/?");
      }
    }

    sb.append('$');
    GLOBS_CACHE.put(glob, sb.toString());

    return sb.toString();
  }

  @Override
  public void dispose() {
    clearCache();
  }

  public void clearCache() {
    GLOBS_CACHE.clear();
    PATTERNS_CACHE.clear();
  }

  public static PatternCache getInstance(@NotNull Project project) {
    return project.getService(PatternCache.class);
  }
}
