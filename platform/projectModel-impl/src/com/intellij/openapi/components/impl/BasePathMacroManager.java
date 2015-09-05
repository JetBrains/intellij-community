/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.SmartHashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.*;

public class BasePathMacroManager extends PathMacroManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.BasePathMacroManager");

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
    for (Map.Entry<String, String> entry : PathMacroUtil.getGlobalSystemMacros().entrySet()) {
      result.addMacroExpand(entry.getKey(), entry.getValue());
    }
    getPathMacros().addMacroExpands(result);
    return result;
  }

  @NotNull
  protected ReplacePathToMacroMap getReplacePathMap() {
    ReplacePathToMacroMap result = new ReplacePathToMacroMap();
    for (Map.Entry<String, String> entry : PathMacroUtil.getGlobalSystemMacros().entrySet()) {
      result.addMacroReplacement(entry.getValue(), entry.getKey());
    }
    getPathMacros().addMacroReplacements(result);
    return result;
  }

  @NotNull
  @Override
  public TrackingPathMacroSubstitutor createTrackingSubstitutor() {
    return new MyTrackingPathMacroSubstitutor();
  }

  @Override
  public String expandPath(final String path) {
    return getExpandMacroMap().substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  @Override
  public String collapsePath(@Nullable String path) {
    return getReplacePathMap().substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  @Override
  public void collapsePathsRecursively(@NotNull final Element element) {
    getReplacePathMap().substitute(element, SystemInfo.isFileSystemCaseSensitive, true);
  }

  @NotNull
  @Override
  public String collapsePathsRecursively(@NotNull final String text) {
    return getReplacePathMap().substituteRecursively(text, SystemInfo.isFileSystemCaseSensitive);
  }

  @Override
  public void expandPaths(@NotNull final Element element) {
    getExpandMacroMap().substitute(element, SystemInfo.isFileSystemCaseSensitive);
  }

  @Override
  public void collapsePaths(@NotNull final Element element) {
    getReplacePathMap().substitute(element, SystemInfo.isFileSystemCaseSensitive);
  }

  @NotNull
  private PathMacrosImpl getPathMacros() {
    if (myPathMacros == null) {
      myPathMacros = PathMacrosImpl.getInstanceEx();
    }
    return myPathMacros;
  }

  private class MyTrackingPathMacroSubstitutor implements TrackingPathMacroSubstitutor {
    private final String myLock = new String("MyTrackingPathMacroSubstitutor.lock");

    private final MultiMap<String, String> myMacroToComponentNames = MultiMap.createSet();
    private final MultiMap<String, String> myComponentNameToMacros = MultiMap.createSet();

    @Override
    public void reset() {
      synchronized (myLock) {
        myMacroToComponentNames.clear();
        myComponentNameToMacros.clear();
      }
    }

    @Override
    public String expandPath(final String path) {
      return getExpandMacroMap().substitute(path, SystemInfo.isFileSystemCaseSensitive);
    }

    @Override
    public String collapsePath(@Nullable String path) {
      return getReplacePathMap().substitute(path, SystemInfo.isFileSystemCaseSensitive);
    }

    @Override
    public void expandPaths(@NotNull final Element element) {
      getExpandMacroMap().substitute(element, SystemInfo.isFileSystemCaseSensitive);
    }

    @Override
    public void collapsePaths(@NotNull final Element element) {
      getReplacePathMap().substitute(element, SystemInfo.isFileSystemCaseSensitive, false, Holder.FILTER);
    }

    public int hashCode() {
      return getExpandMacroMap().hashCode();
    }

    @Override
    public void invalidateUnknownMacros(@NotNull Set<String> macros) {
      synchronized (myLock) {
        for (String macro : macros) {
          Collection<String> componentNames = myMacroToComponentNames.remove(macro);
          if (!ContainerUtil.isEmpty(componentNames)) {
            for (String component : componentNames) {
              myComponentNameToMacros.remove(component);
            }
          }
        }
      }
    }

    @NotNull
    @Override
    public Set<String> getComponents(@NotNull Collection<String> macros) {
      synchronized (myLock) {
        Set<String> result = new SmartHashSet<String>();
        for (String macro : macros) {
          result.addAll(myMacroToComponentNames.get(macro));
        }
        return result;
      }
    }

    @NotNull
    @Override
    public Set<String> getUnknownMacros(@Nullable String componentName) {
      synchronized (myLock) {
        Set<String> list = componentName == null ? myMacroToComponentNames.keySet() : (Set<String>)myComponentNameToMacros.get(componentName);
        return ContainerUtil.isEmpty(list) ? Collections.<String>emptySet() : Collections.unmodifiableSet(list);
      }
    }

    @Override
    public void addUnknownMacros(@NotNull String componentName, @NotNull Collection<String> unknownMacros) {
      if (unknownMacros.isEmpty()) {
        return;
      }
      
      LOG.debug("Registering unknown macros " + new ArrayList<String>(unknownMacros) + " in component " + componentName);

      synchronized (myLock) {
        for (String unknownMacro : unknownMacros) {
          myMacroToComponentNames.putValue(unknownMacro, componentName);
        }

        myComponentNameToMacros.putValues(componentName, unknownMacros);
      }
    }
  }

  protected static boolean pathsEqual(@Nullable String path1, @Nullable String path2) {
    return path1 != null && path2 != null &&
           FileUtil.pathsEqual(FileUtil.toSystemIndependentName(path1), FileUtil.toSystemIndependentName(path2));
  }
}
