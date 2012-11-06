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
package com.intellij.application.options;

import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.CompositePathMacroFilter;
import com.intellij.openapi.components.PathMacroMap;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.containers.ContainerUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
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

  private static final String FILE_PROTOCOL = "file:";
  private static final String JAR_PROTOCOL = "jar:";

  private PathMacrosCollector() {
    myMatcher = MACRO_PATTERN.matcher("");
  }

  @NotNull
  public static Set<String> getMacroNames(@NotNull final Element e) {
    return getMacroNames(e, new CompositePathMacroFilter(Extensions.getExtensions(MACRO_FILTER_EXTENSION_POINT_NAME)),
                         PathMacros.getInstance());
  }

  public static Set<String> getMacroNames(Element root, @Nullable PathMacroFilter filter, @NotNull final PathMacros pathMacros) {
    final PathMacrosCollector collector = new PathMacrosCollector();
    collector.substitute(root, true, false, filter);
    final HashSet<String> result = new HashSet<String>(collector.myMacroMap.keySet());
    result.removeAll(pathMacros.getSystemMacroNames());
    result.removeAll(pathMacros.getLegacyMacroNames());
    result.removeAll(PathMacrosImpl.getToolMacroNames());
    result.removeAll(pathMacros.getIgnoredMacroNames());
    return result;
  }

  @Override
  public String substituteRecursively(String text, boolean caseSensitive) {
    if (text == null || text.isEmpty()) return text;

    myMatcher.reset(text);
    while (myMatcher.find()) {
      final String macroName = myMatcher.group(1);
      myMacroMap.put(macroName, null);
    }

    return text;
  }

  @Override
  public String substitute(String text, boolean caseSensitive) {
    if (text == null || text.isEmpty()) return text;

    String protocol = null;
    if (text.length() > 7 && text.charAt(0) == 'f') {
      protocol = FILE_PROTOCOL;
    } else if (text.length() > 6 && text.charAt(0) == 'j') {
      protocol = JAR_PROTOCOL;
    } else if ('$' != text.charAt(0)) {
      return text;
    }

    if (protocol != null && !text.startsWith(protocol)) return text;

    myMatcher.reset(text);
    if (myMatcher.find()) {
      final String macroName = myMatcher.group(1);
      myMacroMap.put(macroName, null);
    }

    return text;
  }

  @Override
  public int hashCode() {
    return myMacroMap.hashCode();
  }
}
