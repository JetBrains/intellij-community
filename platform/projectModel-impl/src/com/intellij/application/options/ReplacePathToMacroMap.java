/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.components.PathMacroMap;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *
 * @see PathMacrosImpl#addMacroReplacements(ReplacePathToMacroMap)
 * @see com.intellij.openapi.components.PathMacroManager
 */
public class ReplacePathToMacroMap extends PathMacroMap {
  private List<String> myPathsIndex = null;
  private final Map<String, String> myMacroMap = ContainerUtilRt.newLinkedHashMap();

  @NonNls public static final String[] PROTOCOLS;
  static {
    List<String> protocols = new ArrayList<>();
    protocols.add("file");
    protocols.add("jar");
    if (Extensions.getRootArea().hasExtensionPoint(PathMacroExpandableProtocolBean.EP_NAME.getName())) {
      for (PathMacroExpandableProtocolBean bean : PathMacroExpandableProtocolBean.EP_NAME.getExtensions()) {
        protocols.add(bean.protocol);
      }
    }
    PROTOCOLS = ArrayUtil.toStringArray(protocols);
  }

  public void addMacroReplacement(String path, String macroName) {
    addReplacement(FileUtil.toSystemIndependentName(path), "$" + macroName + "$", true);
  }

  public void addReplacement(String path, String macroExpr, boolean overwrite) {
    path = StringUtil.trimEnd(path, "/");
    putIfAbsent(path, macroExpr, overwrite);
    for (String protocol : PROTOCOLS) {
      putIfAbsent(protocol + ":" + path, protocol + ":" + macroExpr, overwrite);
      putIfAbsent(protocol + ":/" + path, protocol + ":/" + macroExpr, overwrite);
      putIfAbsent(protocol + "://" + path, protocol + "://" + macroExpr, overwrite);
    }
  }

  private void putIfAbsent(final String path, final String substitution, final boolean overwrite) {
    if (overwrite || !myMacroMap.containsKey(path)) {
      myMacroMap.put(path, substitution);
    }
  }

  @Override
  public String substitute(@Nullable String text, boolean caseSensitive) {
    if (text == null) {
      //noinspection ConstantConditions
      return null;
    }

    for (final String path : getPathIndex()) {
      text = replacePathMacro(text, path, caseSensitive);
    }
    return text;
  }

  private String replacePathMacro(@NotNull String text, @NotNull final String path, boolean caseSensitive) {
    if (text.length() < path.length() || path.isEmpty()) {
      return text;
    }

    boolean startsWith = caseSensitive ? text.startsWith(path) : StringUtil.startsWithIgnoreCase(text, path);

    if (!startsWith) return text;

    //check that this is complete path (ends with "/" or "!/")
    // do not collapse partial paths, i.e. do not substitute "/a/b/cd" in paths like "/a/b/cdeFgh"
    int endOfOccurrence = path.length();
    final boolean isWindowsRoot = path.endsWith(":/");
    if (!isWindowsRoot &&
        endOfOccurrence < text.length() &&
        text.charAt(endOfOccurrence) != '/' &&
        !text.substring(endOfOccurrence).startsWith("!/")) {
      return text;
    }

    return myMacroMap.get(path) + text.substring(endOfOccurrence);
  }

  @NotNull
  @Override
  public String substituteRecursively(@NotNull String text, final boolean caseSensitive) {
    for (final String path : getPathIndex()) {
      text = replacePathMacroRecursively(text, path, caseSensitive);
    }
    return text;
  }

  private String replacePathMacroRecursively(@NotNull final String text, @NotNull final String path, boolean caseSensitive) {
    if (text.length() < path.length()) {
      return text;
    }

    if (path.isEmpty()) return text;

    final StringBuilder newText = new StringBuilder();
    final boolean isWindowsRoot = path.endsWith(":/");
    int i = 0;
    while (i < text.length()) {
      int occurrenceOfPath = caseSensitive ? text.indexOf(path, i) : StringUtil.indexOfIgnoreCase(text, path, i);
      if (occurrenceOfPath >= 0) {
        int endOfOccurrence = occurrenceOfPath + path.length();
        if (!isWindowsRoot &&
            endOfOccurrence < text.length() &&
            text.charAt(endOfOccurrence) != '/' &&
            text.charAt(endOfOccurrence) != '\"' &&
            text.charAt(endOfOccurrence) != ' ' &&
            !text.substring(endOfOccurrence).startsWith("!/")) {
          newText.append(text.substring(i, endOfOccurrence));
          i = endOfOccurrence;
          continue;
        }
        if (occurrenceOfPath > 0) {
          char prev = text.charAt(occurrenceOfPath - 1);
          if (Character.isLetterOrDigit(prev) || prev == '_') {
            newText.append(text.substring(i, endOfOccurrence));
            i = endOfOccurrence;
            continue;
          }
        }
      }
      if (occurrenceOfPath < 0) {
        if (newText.length() == 0) {
          return text;
        }
        newText.append(text.substring(i));
        break;
      }
      else {
        newText.append(text.substring(i, occurrenceOfPath));
        newText.append(myMacroMap.get(path));
        i = occurrenceOfPath + path.length();
      }
    }
    return newText.toString();
  }

  private static int getIndex(@NotNull final Map.Entry<String, String> s) {
    final String replacement = s.getValue();
    if (replacement.contains("..")) return 1;
    if (replacement.contains("$" + PathMacroUtil.USER_HOME_NAME + "$")) return 1;
    if (replacement.contains("$" + PathMacroUtil.APPLICATION_HOME_DIR + "$")) return 1;
    if (replacement.contains("$" + PathMacroUtil.MODULE_DIR_MACRO_NAME + "$")) return 3;
    if (replacement.contains("$" + PathMacroUtil.PROJECT_DIR_MACRO_NAME + "$")) return 3;
    return 2;
  }

  private static int stripPrefix(@NotNull String key) {
    key = StringUtil.trimStart(key, "jar:");
    key = StringUtil.trimStart(key, "file:");
    while (key.startsWith("/")) {
      key = key.substring(1);
    }
    return key.length();
  }

  @NotNull
  public List<String> getPathIndex() {
    if (myPathsIndex == null || myPathsIndex.size() != myMacroMap.size()) {
      List<Map.Entry<String, String>> entries = new ArrayList<>(myMacroMap.entrySet());

      final TObjectIntHashMap<Map.Entry<String, String>> weights = new TObjectIntHashMap<>();
      for (Map.Entry<String, String> entry : entries) {
        weights.put(entry, getIndex(entry) * 512 + stripPrefix(entry.getKey()));
      }

      ContainerUtil.sort(entries, (o1, o2) -> weights.get(o2) - weights.get(o1));
      myPathsIndex = ContainerUtil.map2List(entries, entry -> entry.getKey());
    }
    return myPathsIndex;
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof ReplacePathToMacroMap)) return false;

    return myMacroMap.equals(((ReplacePathToMacroMap)obj).myMacroMap);
  }

  public int hashCode() {
    return myMacroMap.hashCode();
  }

  public void put(String path, String replacement) {
    myMacroMap.put(path, replacement);
  }

}
