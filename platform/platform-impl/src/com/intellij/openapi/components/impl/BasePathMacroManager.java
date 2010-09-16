/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.PathMacroMap;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.FactoryMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class BasePathMacroManager extends PathMacroManager {
  private PathMacrosImpl myPathMacros;

  protected static void addFileHierarchyReplacements(ReplacePathToMacroMap result,
                                                     String variableName,
                                                     @Nullable String _path, @Nullable String stopAt) {
    if (_path == null) {
      return;
    }

    String macro = "$" + variableName + "$";
    File dir = new File(_path.replace('/', File.separatorChar));
    boolean check = false;
    while (dir != null && dir.getParentFile() != null) {
      @NonNls String path = PathMacroMap.quotePath(dir.getAbsolutePath());
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

  public ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = new ExpandMacroToPathMap();
    result.addMacroExpand(PathMacrosImpl.APPLICATION_HOME_MACRO_NAME, PathManager.getHomePath());
    result.addMacroExpand(PathMacrosImpl.USER_HOME_MACRO_NAME, getUserHome());
    getPathMacros().addMacroExpands(result);
    return result;
  }

  protected static String getUserHome() {
    return SystemProperties.getUserHome();
  }


  public ReplacePathToMacroMap getReplacePathMap() {
    ReplacePathToMacroMap result = new ReplacePathToMacroMap();

    result.addMacroReplacement(PathManager.getHomePath(), PathMacrosImpl.APPLICATION_HOME_MACRO_NAME);
    result.addMacroReplacement(getUserHome(), PathMacrosImpl.USER_HOME_MACRO_NAME);
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


  protected static void putIfAbsent(final ReplacePathToMacroMap result, @NonNls final String pathWithPrefix, @NonNls final String substWithPrefix, final boolean check) {
    if (check && result.get(pathWithPrefix) != null) return;
    result.put(pathWithPrefix, substWithPrefix);
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
}
