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
package com.intellij.openapi.components.impl;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.CompositePathMacroFilter;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.Map;

public class BasePathMacroManager extends PathMacroManager {
  private static class Holder {
    private static final CompositePathMacroFilter FILTER = new CompositePathMacroFilter(Extensions.getExtensions(PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME));
  }

  private PathMacrosImpl myPathMacros;

  public BasePathMacroManager(@Nullable PathMacros pathMacros) {
    myPathMacros = (PathMacrosImpl)pathMacros;
  }

  protected static void addFileHierarchyReplacements(@NotNull ExpandMacroToPathMap result, @NotNull String macroName, @Nullable String path) {
    if (path != null) {
      addFileHierarchyReplacements(result, getLocalFileSystem().findFileByPath(path), '$' + macroName + '$');
    }
  }

  private static void addFileHierarchyReplacements(@NotNull ExpandMacroToPathMap result, @Nullable VirtualFile f, @NotNull String macro) {
    if (f == null) {
      return;
    }

    addFileHierarchyReplacements(result, f.getParent(), macro + "/..");
    result.put(macro, StringUtil.trimEnd(f.getPath(), "/"));
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
  private static VirtualFileSystem getLocalFileSystem() {
    // Use VFM directly because of mocks in tests.
    return VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL);
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
  protected ReplacePathToMacroMap getReplacePathMap() {
    ReplacePathToMacroMap result = new ReplacePathToMacroMap();
    getPathMacros().addMacroReplacements(result);
    for (Map.Entry<String, String> entry : PathMacroUtil.getGlobalSystemMacros().entrySet()) {
      result.addMacroReplacement(entry.getValue(), entry.getKey());
    }
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
