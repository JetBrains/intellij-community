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
package com.intellij.util;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public class PathMappingSettings implements Cloneable {
  private List<PathMapping> myPathMappings;

  public PathMappingSettings(List<PathMapping> pathMappings) {
    myPathMappings = create(pathMappings);
  }

  private List<PathMapping> create(List<PathMapping> mappings) {
    List<PathMapping> result = ContainerUtil.newArrayList();
    for (PathMapping m : mappings) {
      if (m != null && !isEmpty(m.myLocalRoot, m.myRemoteRoot)) {
        result.add(m);
      }
    }
    return result;
  }

  public PathMappingSettings() {
    myPathMappings = ContainerUtil.newArrayList();
  }

  public String convertToLocal(String remotePath) {
    for (PathMapping mapping : myPathMappings) {
      if (mapping.canReplaceRemote(remotePath)) {
        return mapping.mapToLocal(remotePath);
      }
    }

    return remotePath;
  }

  public String convertToRemote(String localPath) {
    for (PathMapping mapping : myPathMappings) {
      if (mapping.canReplaceLocal(localPath)) {
        return mapping.mapToRemote(localPath);
      }
    }
    return localPath;
  }

  public void add(PathMapping mapping) {
    if (isEmpty(mapping.myLocalRoot, mapping.myRemoteRoot)) {
      return;
    }
    myPathMappings.add(mapping);
  }

  public void addMapping(String local, String remote) {
    PathMapping mapping = new PathMapping(local, remote);
    if (isEmpty(mapping.myLocalRoot, mapping.myRemoteRoot)) {
      return;
    }
    myPathMappings.add(mapping);
  }

  public boolean canReplaceRemote(String remotePath) {
    for (PathMapping mapping : myPathMappings) {
      if (mapping.canReplaceRemote(remotePath)) {
        return true;
      }
    }
    return false;
  }

  public boolean canReplaceLocal(String localPath) {
    for (PathMapping mapping : myPathMappings) {
      if (mapping.canReplaceLocal(localPath)) {
        return true;
      }
    }
    return false;
  }

  private static String norm(String s) {
    return FileUtil.toSystemIndependentName(s);
  }

  private static String normReplace(String s) {
    return FileUtil.toSystemIndependentName(s);
  }


  public boolean isUseMapping() {
    return myPathMappings.size() > 0;
  }

  public List<PathMapping> getPathMappings() {
    return myPathMappings;
  }

  public void setPathMappings(List<PathMapping> pathMappings) {
    myPathMappings = create(pathMappings);
  }

  public static String mapToLocal(String path, String remoteRoot, String localRoot) {
    if (isEmpty(localRoot, remoteRoot)) {
      return path;
    }
    path = norm(path);
    String remotePrefix = norm(remoteRoot);
    path = path.replace(remotePrefix, norm(localRoot));
    return path;
  }

  public static boolean isEmpty(String localRoot, String remoteRoot) {
    return StringUtil.isEmpty(localRoot) || StringUtil.isEmpty(remoteRoot);
  }

  @Tag("mapping")
  public static class PathMapping {
    private String myLocalRoot;
    private String myRemoteRoot;

    public PathMapping() {
    }

    public PathMapping(String localRoot, String remoteRoot) {
      myLocalRoot = normalize(localRoot);
      myRemoteRoot = normalize(remoteRoot);
    }

    @Nullable
    private static String normalize(@Nullable String path) {
      if (path == null) {
        return null;
      }
      else {
        return trimSlash(FileUtil.toSystemIndependentName(path));
      }
    }

    @Attribute("local-root")
    public String getLocalRoot() {
      return myLocalRoot;
    }

    @Attribute("remote-root")
    public String getRemoteRoot() {
      return myRemoteRoot;
    }

    public void setLocalRoot(String localRoot) {
      myLocalRoot = localRoot;
    }

    public void setRemoteRoot(String remoteRoot) {
      myRemoteRoot = remoteRoot;
    }

    public String mapToLocal(@NotNull String path) {
      return PathMappingSettings.mapToLocal(path, myRemoteRoot, myLocalRoot);
    }

    public boolean canReplaceLocal(@NotNull String path) {
      if (isEmpty()) {
        return false;
      }

      path = norm(path);

      String localPrefix = norm(myLocalRoot);
      return localPrefix.length() > 0 && path.startsWith(localPrefix);
    }

    public String mapToRemote(@NotNull String path) {
      if (isEmpty()) {
        return path;
      }

      return norm(path).replace(norm(myLocalRoot), norm(myRemoteRoot));
    }

    private boolean isEmpty() {
      return PathMappingSettings.isEmpty(myLocalRoot, myRemoteRoot);
    }

    private static String trimSlash(String s) {
      return StringUtil.trimEnd(s, "/");
    }

    public boolean canReplaceRemote(String path) {
      if (isEmpty()) {
        return false;
      }

      path = norm(path);
      String remotePrefix = norm(myRemoteRoot);
      return myRemoteRoot.length() > 0 && path.startsWith(remotePrefix);
    }

    @Override
    public PathMapping clone() {
      return new PathMapping(myLocalRoot, myRemoteRoot);
    }
  }
}
