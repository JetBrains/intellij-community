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
package com.intellij.application.options;

import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.openapi.components.CompositePathMacroFilter;
import com.intellij.openapi.components.PathMacroMap;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.SmartHashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 6, 2004
 */
public class PathMacrosCollector extends PathMacroMap {
  public static final ExtensionPointName<PathMacroFilter> MACRO_FILTER_EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.pathMacroFilter");
  public static final Pattern MACRO_PATTERN = Pattern.compile("\\$([\\w\\-\\.]+?)\\$");

  private final Matcher myMatcher;
  private final Map<String, String> myMacroMap = ContainerUtilRt.newLinkedHashMap();

  private PathMacrosCollector() {
    myMatcher = MACRO_PATTERN.matcher("");
  }

  @NotNull
  public static Set<String> getMacroNames(@NotNull final Element e) {
    return getMacroNames(e, new CompositePathMacroFilter(Extensions.getExtensions(MACRO_FILTER_EXTENSION_POINT_NAME)),
                         PathMacrosImpl.getInstanceEx());
  }

  @NotNull
  public static Set<String> getMacroNames(Element root, @Nullable PathMacroFilter filter, @NotNull PathMacrosImpl pathMacros) {
    final PathMacrosCollector collector = new PathMacrosCollector();
    collector.substitute(root, true, false, filter);
    Set<String> preResult = collector.myMacroMap.keySet();
    if (preResult.isEmpty()) {
      return Collections.emptySet();
    }

    Set<String> result = new SmartHashSet<>(preResult);
    result.removeAll(pathMacros.getSystemMacroNames());
    result.removeAll(pathMacros.getLegacyMacroNames());
    result.removeAll(pathMacros.getToolMacroNames());
    result.removeAll(pathMacros.getIgnoredMacroNames());
    return result;
  }

  @NotNull
  @Override
  public String substituteRecursively(@NotNull String text, boolean caseSensitive) {
    if (StringUtil.isEmpty(text)) {
      return text;
    }

    myMatcher.reset(text);
    while (myMatcher.find()) {
      myMacroMap.put(myMatcher.group(1), null);
    }

    return text;
  }

  @Override
  public String substitute(String text, boolean caseSensitive) {
    if (StringUtil.isEmpty(text)) {
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
