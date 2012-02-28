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
package com.intellij.openapi.components.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.FactoryMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class BasePathMacroManager extends PathMacroManager {
  private PathMacrosImpl myPathMacros;

  public BasePathMacroManager(@Nullable PathMacros pathMacros) {
    myPathMacros = (PathMacrosImpl)pathMacros;
  }

  protected static void addFileHierarchyReplacements(ExpandMacroToPathMap result, String macroName, @Nullable String path) {
    if (path == null) return;
    final File f = new File(FileUtil.toSystemDependentName(path));
    addFileHierarchyReplacements(result, f, "$" + macroName + "$");
  }

  protected static void addFileHierarchyReplacements(ExpandMacroToPathMap result, @Nullable File f, String macro) {
    if (f == null) return;
    addFileHierarchyReplacements(result, f.getParentFile(), macro + "/..");

    final String path = FileUtil.toSystemIndependentName(f.getAbsolutePath());
    String s = macro;
    if (StringUtil.endsWithChar(path, '/')) s += "/";
    result.put(s, path);
  }

  protected static void addFileHierarchyReplacements(ReplacePathToMacroMap result, String macroName, @Nullable String path, @Nullable String stopAt) {
    if (path == null) return;

    String macro = "$" + macroName + "$";
    File dir = new File(FileUtil.toSystemDependentName(path));
    boolean check = false;
    while (dir != null && dir.getParentFile() != null) {
      path = FileUtil.toSystemIndependentName(dir.getAbsolutePath());

      String s = macro;
      if (StringUtil.endsWithChar(path, '/')) s += "/";

      putIfAbsent(result, "file:" + path, "file:" + s, check);
      putIfAbsent(result, "file:/" + path, "file:/" + s, check);
      putIfAbsent(result, "file://" + path, "file://" + s, check);
      putIfAbsent(result, "jar:" + path, "jar:" + s, check);
      putIfAbsent(result, "jar:/" + path, "jar:/" + s, check);
      putIfAbsent(result, "jar://" + path, "jar://" + s, check);
      if (!path.equalsIgnoreCase("e:/") && !path.equalsIgnoreCase("r:/") && !path.equalsIgnoreCase("p:/")) {
        putIfAbsent(result, path, s, check);
      }

      if (dir.getPath().equals(stopAt)) {
        break;
      }

      macro += "/..";
      check = true;
      dir = dir.getParentFile();
    }
  }

  // todo: make protected
  public ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = new ExpandMacroToPathMap();
    for (Map.Entry<String, String> entry : PathMacrosImpl.getGlobalSystemMacros().entrySet()) {
      result.addMacroExpand(entry.getKey(), entry.getValue());
    }
    getPathMacros().addMacroExpands(result);
    return result;
  }

  // todo: make protected
  public ReplacePathToMacroMap getReplacePathMap() {
    ReplacePathToMacroMap result = new ReplacePathToMacroMap();
    for (Map.Entry<String, String> entry : PathMacrosImpl.getGlobalSystemMacros().entrySet()) {
      result.addMacroReplacement(entry.getValue(), entry.getKey());
    }
    getPathMacros().addMacroReplacements(result);
    return result;
  }

  public TrackingPathMacroSubstitutor createTrackingSubstitutor() {
    return new MyTrackingPathMacroSubstitutor();
  }

  public String expandPath(final String path) {
    return getExpandMacroMap().substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  public String collapsePath(final String path) {
    return getReplacePathMap().substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  public void collapsePathsRecursively(final Element element) {
    getReplacePathMap().substitute(element, SystemInfo.isFileSystemCaseSensitive, true);
  }

  public void expandPaths(final Element element) {
    getExpandMacroMap().substitute(element, SystemInfo.isFileSystemCaseSensitive);
  }

  public void collapsePaths(final Element element) {
    getReplacePathMap().substitute(element, SystemInfo.isFileSystemCaseSensitive);
  }

  public PathMacrosImpl getPathMacros() {
    if (myPathMacros == null) {
      myPathMacros = PathMacrosImpl.getInstanceEx();
    }

    return myPathMacros;
  }

  protected static void putIfAbsent(final ReplacePathToMacroMap result,
                                    @NonNls final String pathWithPrefix,
                                    @NonNls final String substitutionWithPrefix,
                                    final boolean check) {
    if (check && result.get(pathWithPrefix) != null) return;
    result.put(pathWithPrefix, substitutionWithPrefix);
  }

  private class MyTrackingPathMacroSubstitutor implements TrackingPathMacroSubstitutor {
    private final Map<String, Set<String>> myMacroToComponentNames = new FactoryMap<String, Set<String>>() {
      @Override
      protected Set<String> create(String key) {
        return new HashSet<String>();
      }
    };

    private final Map<String, Set<String>> myComponentNameToMacros = new FactoryMap<String, Set<String>>() {
      @Override
      protected Set<String> create(String key) {
        return new HashSet<String>();
      }
    };

    public MyTrackingPathMacroSubstitutor() {
    }

    public void reset() {
      myMacroToComponentNames.clear();
      myComponentNameToMacros.clear();
    }

    public String expandPath(final String path) {
      return getExpandMacroMap().substitute(path, SystemInfo.isFileSystemCaseSensitive);
    }

    public String collapsePath(final String path) {
      return getReplacePathMap().substitute(path, SystemInfo.isFileSystemCaseSensitive);
    }

    public void expandPaths(final Element element) {
      getExpandMacroMap().substitute(element, SystemInfo.isFileSystemCaseSensitive);
    }

    public void collapsePaths(final Element element) {
      getReplacePathMap().substitute(element, SystemInfo.isFileSystemCaseSensitive);
    }

    public int hashCode() {
      return getExpandMacroMap().hashCode();
    }

    public void invalidateUnknownMacros(final Set<String> macros) {
      for (final String macro : macros) {
        final Set<String> components = myMacroToComponentNames.get(macro);
        for (final String component : components) {
          myComponentNameToMacros.remove(component);
        }

        myMacroToComponentNames.remove(macro);
      }
    }

    public Collection<String> getComponents(final Collection<String> macros) {
      final Set<String> result = new HashSet<String>();
      for (String macro : myMacroToComponentNames.keySet()) {
        if (macros.contains(macro)) {
          result.addAll(myMacroToComponentNames.get(macro));
        }
      }

      return result;
    }

    public Collection<String> getUnknownMacros(final String componentName) {
      final Set<String> result = new HashSet<String>();
      result.addAll(componentName == null ? myMacroToComponentNames.keySet() : myComponentNameToMacros.get(componentName));
      return Collections.unmodifiableCollection(result);
    }

    public void addUnknownMacros(final String componentName, final Collection<String> unknownMacros) {
      if (unknownMacros.isEmpty()) return;
      
      for (String unknownMacro : unknownMacros) {
        final Set<String> stringList = myMacroToComponentNames.get(unknownMacro);
        stringList.add(componentName);
      }

      myComponentNameToMacros.get(componentName).addAll(unknownMacros);
    }
  }

  protected static boolean pathsEqual(@Nullable String path1, @Nullable String path2) {
    return path1 != null && path2 != null &&
           FileUtil.pathsEqual(FileUtil.toSystemIndependentName(path1), FileUtil.toSystemIndependentName(path2));
  }
}
