// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.openapi.components.CompositePathMacroFilter;
import com.intellij.openapi.components.PathMacroMap;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.Strings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 */
public final class PathMacrosCollector extends PathMacroMap {
  public static final ExtensionPointName<PathMacroFilter> MACRO_FILTER_EXTENSION_POINT_NAME = new ExtensionPointName<>("com.intellij.pathMacroFilter");
  public static final Pattern MACRO_PATTERN = Pattern.compile("\\$([\\w\\-.]+?)\\$");

  private final Matcher myMatcher;
  private final Map<String, String> myMacroMap = new LinkedHashMap<>();

  private PathMacrosCollector() {
    myMatcher = MACRO_PATTERN.matcher("");
  }

  public static @NotNull Set<String> getMacroNames(final @NotNull Element e) {
    return getMacroNames(e, new CompositePathMacroFilter(MACRO_FILTER_EXTENSION_POINT_NAME.getExtensionList()),
                         PathMacrosImpl.getInstanceEx());
  }

  public static @NotNull Set<String> getMacroNames(@NotNull Element root, @Nullable PathMacroFilter filter, @NotNull PathMacrosImpl pathMacros) {
    PathMacrosCollector collector = new PathMacrosCollector();
    collector.substitute(root, true, false, filter);
    Set<String> preResult = collector.myMacroMap.keySet();
    if (preResult.isEmpty()) {
      return Collections.emptySet();
    }

    Set<String> result = new HashSet<>(preResult);
    result.removeAll(pathMacros.getSystemMacroNames());
    result.removeAll(pathMacros.getLegacyMacroNames());
    pathMacros.removeToolMacroNames(result);
    result.removeAll(pathMacros.getIgnoredMacroNames());
    return result;
  }

  @Override
  public @NotNull CharSequence substituteRecursively(@NotNull String text, boolean caseSensitive) {
    if (Strings.isEmpty(text)) {
      return text;
    }

    myMatcher.reset(text);
    while (myMatcher.find()) {
      myMacroMap.put(myMatcher.group(1), null);
    }

    return text;
  }

  @Override
  public @NotNull String substitute(@NotNull String text, boolean caseSensitive) {
    if (Strings.isEmpty(text)) {
      return text;
    }

    int startPos = -1;
    if (text.charAt(0) == '$') {
      startPos = 0;
    }
    else {
      for (String protocol : ReplacePathToMacroMap.PROTOCOLS) {
        if (text.length() > protocol.length() + 4 && text.startsWith(protocol) && text.charAt(protocol.length()) == ':') {
          startPos = protocol.length() + 1;
          if (text.charAt(startPos) == '/') startPos++;
          if (text.charAt(startPos) == '/') startPos++;
        }
      }
    }
    if (startPos < 0) {
      return text;
    }

    myMatcher.reset(text).region(startPos, text.length());
    if (myMatcher.lookingAt()) {
      myMacroMap.put(myMatcher.group(1), null);
    }

    return text;
  }

  @Override
  public int hashCode() {
    return myMacroMap.hashCode();
  }
}
