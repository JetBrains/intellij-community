// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroMap;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @see PathMacrosImpl#addMacroReplacements(ReplacePathToMacroMap)
 * @see com.intellij.openapi.components.PathMacroManager
 */
public final class ReplacePathToMacroMap extends PathMacroMap {
  private List<String> pathIndex = null;
  private final List<String> prefixes;
  private final Map<String, String> myMacroMap = new LinkedHashMap<>();

  public ReplacePathToMacroMap() {
    Application app = ApplicationManager.getApplication();
    if (app != null) {
      PathMacroProtocolHolder.loadAppExtensions$intellij_platform_projectModel_impl(app);
    }
    prefixes = ContainerUtil.map(PathMacroProtocolHolder.getProtocols(), protocol -> protocol + ":");
  }

  @SuppressWarnings("CopyConstructorMissesField")
  public ReplacePathToMacroMap(@NotNull ReplacePathToMacroMap map) {
    this();
    myMacroMap.putAll(map.myMacroMap);
  }

  public void addMacroReplacement(String path, String macroName) {
    addReplacement(FileUtilRt.toSystemIndependentName(path), "$" + macroName + "$", true);
  }

  public void addReplacement(String path, String macroExpr, boolean overwrite) {
    putIfAbsent(Strings.trimEnd(path, "/"), macroExpr, overwrite);
  }

  private void putIfAbsent(final String path, final String substitution, final boolean overwrite) {
    if (overwrite || !myMacroMap.containsKey(path)) {
      myMacroMap.put(path, substitution);
    }
  }

  @Override
  public @NotNull String substitute(@NotNull String text, boolean caseSensitive) {
    for (String path : getPathIndex()) {
      text = replacePathMacro(text, path, caseSensitive);
    }
    return text;
  }

  private @NotNull String replacePathMacro(@NotNull String text, final @NotNull String path, boolean caseSensitive) {
    if (text.length() < path.length() || path.isEmpty()) {
      return text;
    }

    String prefix = matchPrefix(text, path, caseSensitive);
    if (prefix == null) {
      return text;
    }

    //check that this is complete path (ends with "/" or "!/")
    // do not collapse partial paths, i.e., do not substitute "/a/b/cd" in paths like "/a/b/cdeFgh"
    int endOfOccurrence = prefix.length() + path.length();
    final boolean isWindowsRoot = path.endsWith(":/");
    if (!isWindowsRoot &&
        endOfOccurrence < text.length() &&
        text.charAt(endOfOccurrence) != '/' &&
        !(text.charAt(endOfOccurrence) == '!' && text.substring(endOfOccurrence).startsWith("!/"))) {
      return text;
    }

    String s = myMacroMap.get(path);
    if (text.length() > endOfOccurrence) {
      return prefix + s + text.substring(endOfOccurrence);
    }
    else {
      return prefix + s;
    }
  }

  private @Nullable String matchPrefix(String text, String path, boolean caseSensitive) {
    if (startsWith(text, path, caseSensitive, 0)) {
      return "";
    }
    for (String prefix : prefixes) {
      if (startsWith(text, prefix, caseSensitive, 0)) {
        int prefixLength = prefix.length();
        if (startsWith(text, path, caseSensitive, prefixLength)) {
          return prefix;
        }
        if (text.length() > prefixLength && text.charAt(prefixLength) == '/') {
          if (startsWith(text, path, caseSensitive, prefixLength + 1)) {
            return text.substring(0, prefixLength + 1);
          }
          else if (text.length() > prefixLength + 1 && text.charAt(prefixLength + 1) == '/' &&
                   startsWith(text, path, caseSensitive, prefixLength + 2)) {
            return text.substring(0, prefixLength + 2);
          }
        }
        return null;
      }
    }
    return null;
  }

  private static boolean startsWith(@NotNull String text, @NotNull String path, boolean caseSensitive, int offset) {
    return caseSensitive ? text.startsWith(path, offset) : StringUtilRt.startsWithIgnoreCase(text, offset, path);
  }

  @Override
  public @NotNull CharSequence substituteRecursively(@NotNull String text, boolean caseSensitive) {
    CharSequence result = text;
    for (String path : getPathIndex()) {
      result = replacePathMacroRecursively(result, path, caseSensitive);
    }
    return result;
  }

  private CharSequence replacePathMacroRecursively(@NotNull CharSequence text, @NotNull String path, boolean caseSensitive) {
    if ((text.length() < path.length()) || path.isEmpty()) {
      return text;
    }

    StringBuilder newText = new StringBuilder();
    boolean isWindowsRoot = path.endsWith(":/");
    int i = 0;
    while (i < text.length()) {
      int occurrenceOfPath = caseSensitive ? Strings.indexOf(text, path, i) : Strings.indexOfIgnoreCase(text, path, i);
      if (occurrenceOfPath >= 0) {
        int endOfOccurrence = occurrenceOfPath + path.length();
        if (!isWindowsRoot &&
            endOfOccurrence < text.length() &&
            text.charAt(endOfOccurrence) != '/' &&
            text.charAt(endOfOccurrence) != '\"' &&
            text.charAt(endOfOccurrence) != ' ' &&
            !Strings.startsWith(text, endOfOccurrence, "!/")) {
          newText.append(text, i, endOfOccurrence);
          i = endOfOccurrence;
          continue;
        }
        if (occurrenceOfPath > 0) {
          char prev = text.charAt(occurrenceOfPath - 1);
          if (Character.isLetterOrDigit(prev) || prev == '_') {
            newText.append(text, i, endOfOccurrence);
            i = endOfOccurrence;
            continue;
          }
        }
      }
      if (occurrenceOfPath < 0) {
        if (newText.isEmpty()) {
          return text;
        }
        newText.append(text, i, text.length());
        break;
      }
      else {
        newText.append(text, i, occurrenceOfPath);
        newText.append(myMacroMap.get(path));
        i = occurrenceOfPath + path.length();
      }
    }
    return newText;
  }

  private static int getIndex(@NotNull String replacement) {
    if (replacement.contains("..") ||
        replacement.contains("$" + PathMacroUtil.USER_HOME_NAME + "$") ||
        replacement.contains("$" + PathMacroUtil.APPLICATION_HOME_DIR + "$") ||
        replacement.contains("$" + PathMacrosImpl.MAVEN_REPOSITORY + "$")) {
      return 1;
    }
    if (replacement.contains(PathMacroUtil.DEPRECATED_MODULE_DIR) ||
        replacement.contains("$" + PathMacroUtil.PROJECT_DIR_MACRO_NAME + "$")) {
      return 3;
    }
    return 2;
  }

  private static int stripPrefix(@NotNull String key) {
    while (key.startsWith("/")) {
      key = key.substring(1);
    }
    return key.length();
  }

  private @NotNull List<String> getPathIndex() {
    if (pathIndex != null && pathIndex.size() == myMacroMap.size()) {
      return pathIndex;
    }

    List<Map.Entry<String, String>> entries = new ArrayList<>(myMacroMap.entrySet());

    Object2IntMap<String> weights = new Object2IntOpenHashMap<>(entries.size());
    for (Map.Entry<String, String> entry : entries) {
      weights.put(entry.getKey(), getIndex(entry.getValue()) * 512 + stripPrefix(entry.getKey()));
    }

    entries.sort((o1, o2) -> weights.getInt(o2.getKey()) - weights.getInt(o1.getKey()));
    pathIndex = ContainerUtil.map(entries, entry -> entry.getKey());
    return pathIndex;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof ReplacePathToMacroMap)) return false;
    return myMacroMap.equals(((ReplacePathToMacroMap)obj).myMacroMap);
  }

  @Override
  public int hashCode() {
    return myMacroMap.hashCode();
  }

  public void put(String path, String replacement) {
    myMacroMap.put(path, replacement);
  }

  @Override
  public String toString() {
    return "macroMap: " + myMacroMap + "\n\npathsIndex: " + StringUtil.join(pathIndex, "\n");
  }
}
