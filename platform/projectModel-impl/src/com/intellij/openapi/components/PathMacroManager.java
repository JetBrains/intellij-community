// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.PathUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.Map;

public class PathMacroManager implements PathMacroSubstitutor {
  public static @NotNull PathMacroManager getInstance(@NotNull ComponentManager componentManager) {
    return componentManager.getService(PathMacroManager.class);
  }

  private static class Holder {
    private static @NotNull CompositePathMacroFilter createFilter() {
      return new CompositePathMacroFilter(PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME.getExtensionList());
    }
  }

  private PathMacrosImpl myPathMacros;

  private ReplacePathToMacroMap myReplacePathToMacroMap;
  private long myPathMacrosModificationCount;

  public PathMacroManager(@Nullable PathMacros pathMacros) {
    myPathMacros = (PathMacrosImpl)pathMacros;
  }

  @NotNull
  public PathMacroFilter getMacroFilter() {
    return Holder.createFilter();
  }

  protected static void addFileHierarchyReplacements(@NotNull ExpandMacroToPathMap result, @NotNull String macroName, @SystemIndependent @Nullable String path) {
    if (path != null) {
      doAddFileHierarchyReplacements(result, Strings.trimEnd(path, "/"), '$' + macroName + '$');
    }
  }

  private static void doAddFileHierarchyReplacements(@NotNull ExpandMacroToPathMap result, @NotNull String path, @NotNull String macro) {
    String parentPath = PathUtilRt.getParentPath(path);
    if (!parentPath.isEmpty()) {
      doAddFileHierarchyReplacements(result, parentPath, macro + "/..");
    }
    result.put(macro, path);
  }

  protected static void addFileHierarchyReplacements(ReplacePathToMacroMap result, String macroName, @Nullable String path, @Nullable String stopAt) {
    if (path == null) return;

    String macro = '$' + macroName + '$';
    path = Strings.trimEnd(FileUtil.toSystemIndependentName(path), "/");
    boolean overwrite = true;
    while (Strings.isNotEmpty(path) && path.contains("/") && !"/".equals(path)) {
      result.addReplacement(path, macro, overwrite);
      if (path.equals(stopAt)) break;
      macro += "/..";
      path = OSAgnosticPathUtil.getParent(path);
      overwrite = false;
    }
  }

  @NotNull
  public ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = new ExpandMacroToPathMap();
    getPathMacros().addMacroExpands(result);
    for (Map.Entry<String, String> entry : PathMacroUtil.getGlobalSystemMacros().entrySet()) {
      result.addMacroExpand(entry.getKey(), entry.getValue());
    }
    return result;
  }

  @NotNull
  public final synchronized ReplacePathToMacroMap getReplacePathMap() {
    long pathMacrosModificationCount = getPathMacros().getModificationCount();
    if (myReplacePathToMacroMap != null && pathMacrosModificationCount == myPathMacrosModificationCount) {
      return myReplacePathToMacroMap;
    }

    myReplacePathToMacroMap = computeReplacePathMap();
    myPathMacrosModificationCount = pathMacrosModificationCount;
    return myReplacePathToMacroMap;
  }

  @NotNull
  protected ReplacePathToMacroMap computeReplacePathMap() {
    ReplacePathToMacroMap result = new ReplacePathToMacroMap();
    getPathMacros().addMacroReplacements(result);
    for (Map.Entry<String, String> entry : PathMacroUtil.getGlobalSystemMacros().entrySet()) {
      result.addMacroReplacement(entry.getValue(), entry.getKey());
    }
    return result;
  }

  @Override
  @Contract("null -> null; !null -> !null")
  public String expandPath(@Nullable String text) {
    if (Strings.isEmpty(text)) {
      return text;
    }
    return getExpandMacroMap().substitute(text, SystemInfo.isFileSystemCaseSensitive);
  }

  @Contract("null, _ -> null; !null, _ -> !null")
  @Override
  public String collapsePath(@Nullable String text, boolean recursively) {
    if (Strings.isEmpty(text)) {
      return text;
    }
    return getReplacePathMap().substitute(text, SystemInfo.isFileSystemCaseSensitive, recursively).toString();
  }

  @Override
  public void expandPaths(@NotNull Element element) {
    getExpandMacroMap().substitute(element, SystemInfo.isFileSystemCaseSensitive);
  }

  @Override
  public void collapsePaths(@NotNull Element element, boolean recursively) {
    collapsePaths(element, recursively, getReplacePathMap());
  }

  public static void collapsePaths(@NotNull Element element, boolean recursively, @NotNull ReplacePathToMacroMap map) {
    map.substitute(element, SystemInfo.isFileSystemCaseSensitive, recursively, Holder.createFilter());
  }

  @NotNull
  private PathMacrosImpl getPathMacros() {
    if (myPathMacros == null) {
      myPathMacros = PathMacrosImpl.getInstanceEx();
    }
    return myPathMacros;
  }

  protected static boolean pathsEqual(@Nullable String path1, @Nullable String path2) {
    return path1 != null && path2 != null &&
           FileUtil.pathsEqual(FileUtil.toSystemIndependentName(path1), FileUtil.toSystemIndependentName(path2));
  }
}