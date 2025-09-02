// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.impl.ModulePathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.PathUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.*;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.Map;

public class PathMacroManager implements PathMacroSubstitutor {
  public static @NotNull PathMacroManager getInstance(@NotNull ComponentManager componentManager) {
    if (componentManager instanceof Module module) {
      return getInstance(module);
    }
    else {
      return componentManager.getService(PathMacroManager.class);
    }
  }

  public static @NotNull PathMacroManager getInstance(@NotNull Module module) {
    return new ModulePathMacroManager(module);
  }

  private PathMacrosImpl pathMacros;

  private ReplacePathToMacroMap replacePathToMacroMap;
  private long pathMacrosModificationCount;

  public PathMacroManager(@Nullable PathMacros pathMacros) {
    this.pathMacros = (PathMacrosImpl)pathMacros;
  }

  private static @NotNull CompositePathMacroFilter createFilter() {
    return PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME.computeIfAbsent(PathMacroManager.class, () -> {
      return new CompositePathMacroFilter(PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME.getExtensionList());
    });
  }

  public @NotNull PathMacroFilter getMacroFilter() {
    return createFilter();
  }

  @ApiStatus.Internal
  public static void addFileHierarchyReplacements(@NotNull ExpandMacroToPathMap result, @NotNull String macroName, @SystemIndependent @Nullable String path) {
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

  @ApiStatus.Internal
  protected static void addFileHierarchyReplacements(ReplacePathToMacroMap result, String macroName, @Nullable String path, @Nullable String stopAt) {
    if (path == null) return;

    String macro = '$' + macroName + '$';
    path = Strings.trimEnd(FileUtilRt.toSystemIndependentName(path), "/");
    boolean overwrite = true;
    while (Strings.isNotEmpty(path) && path.contains("/") && !"/".equals(path)) {
      result.addReplacement(path, macro, overwrite);
      if (path.equals(stopAt)) break;
      macro += "/..";
      path = OSAgnosticPathUtil.getParent(path);
      overwrite = false;
    }
  }

  public @NotNull ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = new ExpandMacroToPathMap();
    getPathMacros().addMacroExpands(result);
    for (Map.Entry<String, String> entry : PathMacroUtil.getGlobalSystemMacros().entrySet()) {
      result.addMacroExpand(entry.getKey(), entry.getValue());
    }
    return result;
  }

  public final synchronized @NotNull ReplacePathToMacroMap getReplacePathMap() {
    long pathMacrosModificationCount = getPathMacros().getModificationCount();
    if (replacePathToMacroMap != null && pathMacrosModificationCount == this.pathMacrosModificationCount) {
      return replacePathToMacroMap;
    }

    replacePathToMacroMap = computeReplacePathMap();
    this.pathMacrosModificationCount = pathMacrosModificationCount;
    return replacePathToMacroMap;
  }

  protected @NotNull ReplacePathToMacroMap computeReplacePathMap() {
    ReplacePathToMacroMap result = new ReplacePathToMacroMap();
    getPathMacros().addMacroReplacements(result);
    for (Map.Entry<String, String> entry : PathMacroUtil.getGlobalSystemMacros().entrySet()) {
      result.addMacroReplacement(entry.getValue(), entry.getKey());
    }
    return result;
  }

  @ApiStatus.Internal
  protected void resetCachedReplacePathMap() {
    replacePathToMacroMap = null;
  }

  @Override
  @Contract("null -> null; !null -> !null")
  public String expandPath(@Nullable String text) {
    if (Strings.isEmpty(text)) {
      return text;
    }
    return getExpandMacroMap().substitute(text, SystemInfoRt.isFileSystemCaseSensitive);
  }

  @Contract("null, _ -> null; !null, _ -> !null")
  @Override
  public String collapsePath(@Nullable String text, boolean recursively) {
    if (Strings.isEmpty(text)) {
      return text;
    }
    return getReplacePathMap().substitute(text, SystemInfoRt.isFileSystemCaseSensitive, recursively).toString();
  }

  @Override
  public void expandPaths(@NotNull Element element) {
    getExpandMacroMap().substitute(element, SystemInfoRt.isFileSystemCaseSensitive);
  }

  @Override
  public void collapsePaths(@NotNull Element element, boolean recursively) {
    collapsePaths(element, recursively, getReplacePathMap());
  }

  public static void collapsePaths(@NotNull Element element, boolean recursively, @NotNull ReplacePathToMacroMap map) {
    map.substitute(element, SystemInfoRt.isFileSystemCaseSensitive, recursively, createFilter());
  }

  private @NotNull PathMacrosImpl getPathMacros() {
    if (pathMacros == null) {
      pathMacros = PathMacrosImpl.getInstanceEx();
    }
    return pathMacros;
  }

  protected static boolean pathsEqual(@Nullable String path1, @Nullable String path2) {
    return path1 != null && path2 != null &&
           FileUtil.pathsEqual(FileUtilRt.toSystemIndependentName(path1), FileUtilRt.toSystemIndependentName(path2));
  }
}