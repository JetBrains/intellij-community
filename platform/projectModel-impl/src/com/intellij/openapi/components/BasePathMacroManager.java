// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.impl.TrackingPathMacroSubstitutorImpl;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtilRt;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.Map;

public class BasePathMacroManager extends PathMacroManager {
  @NotNull
  public static PathMacroManager getInstance(@NotNull ComponentManager componentManager) {
    return (PathMacroManager)componentManager.getPicoContainer().getComponentInstance(PathMacroManager.class);
  }

  private static class Holder {
    private static final CompositePathMacroFilter FILTER = new CompositePathMacroFilter(Extensions.getExtensions(PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME));
  }

  private PathMacrosImpl myPathMacros;

  private ReplacePathToMacroMap myReplacePathToMacroMap;
  private long myPathMacrosModificationCount;

  public BasePathMacroManager(@Nullable PathMacros pathMacros) {
    myPathMacros = (PathMacrosImpl)pathMacros;
  }

  protected static void addFileHierarchyReplacements(@NotNull ExpandMacroToPathMap result, @NotNull String macroName, @SystemIndependent @Nullable String path) {
    if (path != null) {
      doAddFileHierarchyReplacements(result, StringUtil.trimEnd(path, "/"), '$' + macroName + '$');
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
    if (path == null) {
      return;
    }

    String macro = '$' + macroName + '$';
    path = StringUtil.trimEnd(FileUtil.toSystemIndependentName(path), "/");
    boolean overwrite = true;
    while (StringUtil.isNotEmpty(path) && path.contains("/")) {
      result.addReplacement(path, macro, overwrite);

      if (path.equals(stopAt)) {
        break;
      }

      macro += "/..";
      overwrite = false;
      path = StringUtil.getPackageName(path, '/');
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
  public synchronized ReplacePathToMacroMap getReplacePathMap() {
    PathMacrosImpl pathMacros = getPathMacros();

    long pathMacrosModificationCount = pathMacros.getModificationCount();
    if (myReplacePathToMacroMap != null && pathMacrosModificationCount == myPathMacrosModificationCount) {
      return myReplacePathToMacroMap;
    }

    ReplacePathToMacroMap result = new ReplacePathToMacroMap();
    pathMacros.addMacroReplacements(result);
    for (Map.Entry<String, String> entry : PathMacroUtil.getGlobalSystemMacros().entrySet()) {
      result.addMacroReplacement(entry.getValue(), entry.getKey());
    }

    myReplacePathToMacroMap = result;
    myPathMacrosModificationCount = pathMacrosModificationCount;
    return result;
  }

  @NotNull
  @Override
  public TrackingPathMacroSubstitutor createTrackingSubstitutor() {
    return new TrackingPathMacroSubstitutorImpl(this);
  }

  @Override
  @Contract("null -> null; !null -> !null")
  public String expandPath(@Nullable String text) {
    if (StringUtil.isEmpty(text)) {
      return text;
    }
    return getExpandMacroMap().substitute(text, SystemInfo.isFileSystemCaseSensitive);
  }

  @Contract("null, _ -> null; !null, _ -> !null")
  @Override
  public String collapsePath(@Nullable String text, boolean recursively) {
    if (StringUtil.isEmpty(text)) {
      return text;
    }
    return getReplacePathMap().substitute(text, SystemInfo.isFileSystemCaseSensitive, recursively);
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
    map.substitute(element, SystemInfo.isFileSystemCaseSensitive, recursively, Holder.FILTER);
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
