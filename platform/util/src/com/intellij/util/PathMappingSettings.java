/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
public class PathMappingSettings implements Cloneable {

  @NotNull
  private List<PathMapping> myPathMappings;

  public PathMappingSettings(@Nullable final List<PathMapping> pathMappings) {
    myPathMappings = create(pathMappings);
  }

  @NotNull
  private static List<PathMapping> create(@Nullable final List<PathMapping> mappings) {
    List<PathMapping> result = ContainerUtil.newArrayList();
    if (mappings != null) {
      for (PathMapping m : mappings) {
        if (m != null && !areBothEmpty(m.myLocalRoot, m.myRemoteRoot)) {
          result.add(m);
        }
      }
    }
    return result;
  }

  public PathMappingSettings() {
    myPathMappings = ContainerUtil.newArrayList();
  }

  public List<String> convertToRemote(Collection<String> paths) {
    List<String> result = ContainerUtil.newArrayList();
    for (String p: paths) {
      result.add(convertToRemote(p));
    }
    return result;
  }

  public boolean isEmpty() {
    return myPathMappings.isEmpty();
  }

  private static class BestMappingSelector {
    private int myBestWeight = -1;
    private PathMapping myBest = null;

    public void consider(PathMapping mapping, int weight) {
      if (weight > myBestWeight) {
        myBestWeight = weight;
        myBest = mapping;
      }
    }

    @Nullable
    public PathMapping get() {
      return myBest;
    }
  }

  @NotNull
  public String convertToLocal(String remotePath) {
    BestMappingSelector selector = new BestMappingSelector();
    for (PathMapping mapping : myPathMappings) {
      if (mapping.canReplaceRemote(remotePath)) {
        selector.consider(mapping, mapping.getRemoteLen());
      }
    }

    if (selector.get() != null) {
      //noinspection ConstantConditions
      return selector.get().mapToLocal(remotePath);
    }

    return remotePath;
  }

  public String convertToRemote(String localPath) {
    BestMappingSelector selector = new BestMappingSelector();
    for (PathMapping mapping : myPathMappings) {
      if (mapping.canReplaceLocal(localPath)) {
        selector.consider(mapping, mapping.getLocalLen());
      }
    }

    if (selector.get() != null) {
      //noinspection ConstantConditions
      return selector.get().mapToRemote(localPath);
    }

    return localPath;
  }

  public void add(PathMapping mapping) {
    if (areBothEmpty(mapping.myLocalRoot, mapping.myRemoteRoot)) {
      return;
    }
    myPathMappings.add(mapping);
  }

  public void addMapping(String local, String remote) {
    PathMapping mapping = new PathMapping(local, remote);
    add(mapping);
  }

  public void addMappingCheckUnique(String local, String remote) {
    for (PathMapping mapping: myPathMappings) {
      if (pathEquals(local, mapping.getLocalRoot()) && pathEquals(remote, mapping.getRemoteRoot())) {
        return;
      }
    }
    addMapping(local, remote);
  }

  private static boolean pathEquals(@NotNull String path1, @NotNull String path2) {
    return norm(path1).equals(norm(path2));
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

  private static String norm(@NotNull String path) {
    return FileUtil.toSystemIndependentName(path);
  }

  private static String normLocal(@NotNull String path) {
    if (SystemInfo.isWindows) {
      path = path.toLowerCase();
    }

    return norm(path);
  }

  public boolean isUseMapping() {
    return !myPathMappings.isEmpty();
  }

  @NotNull
  public List<PathMapping> getPathMappings() {
    return myPathMappings;
  }

  public void setPathMappings(@Nullable final List<PathMapping> pathMappings) {
    myPathMappings = create(pathMappings);
  }

  @NotNull
  public static String mapToLocal(String path, String remoteRoot, String localRoot) {
    if (areBothEmpty(localRoot, remoteRoot)) {
      return path;
    }
    path = norm(path);
    String remotePrefix = norm(remoteRoot);
    path = path.replace(remotePrefix, norm(localRoot));
    return path;
  }

  public static boolean areBothEmpty(String localRoot, String remoteRoot) {
    return StringUtil.isEmpty(localRoot) || StringUtil.isEmpty(remoteRoot);
  }

  @Nullable
  public static PathMappingSettings readExternal(@Nullable final Element element) {
    if (element == null) {
      return null;
    }

    final Element settingsElement = element.getChild(PathMappingSettings.class.getSimpleName());
    if (settingsElement == null) {
      return null;
    }

    return XmlSerializer.deserialize(settingsElement, PathMappingSettings.class);
  }

  public static void writeExternal(@Nullable final Element element, @Nullable final PathMappingSettings mappings) {
    if (element == null || mappings == null || !mappings.isUseMapping()) {
      return;
    }
    element.addContent(XmlSerializer.serialize(mappings));
  }

  public void addAll(@NotNull PathMappingSettings settings) {
    myPathMappings.addAll(settings.getPathMappings());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PathMappingSettings settings = (PathMappingSettings)o;

    if (!myPathMappings.equals(settings.myPathMappings)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myPathMappings.hashCode();
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

    private int getLocalLen() {
      return myLocalRoot != null ? myLocalRoot.length() : -1;
    }

    private int getRemoteLen() {
      return myRemoteRoot != null ? myRemoteRoot.length() : -1;
    }

    public void setLocalRoot(String localRoot) {
      myLocalRoot = localRoot;
    }

    public void setRemoteRoot(String remoteRoot) {
      myRemoteRoot = remoteRoot;
    }

    @NotNull
    public String mapToLocal(@NotNull String path) {
      return PathMappingSettings.mapToLocal(path, myRemoteRoot, myLocalRoot);
    }

    public boolean canReplaceLocal(@NotNull String path) {
      if (isEmpty()) {
        return false;
      }

      String localPrefix = normLocal(myLocalRoot);
      return !localPrefix.isEmpty() && normLocal(path).startsWith(localPrefix);
    }

    public String mapToRemote(@NotNull String path) {
      if (isEmpty()) {
        return path;
      }

      if (canReplaceLocal(path)) {
        return norm(myRemoteRoot) + norm(path).substring(normLocal(myLocalRoot).length());
      }
      return path;
    }

    private boolean isEmpty() {
      return PathMappingSettings.areBothEmpty(myLocalRoot, myRemoteRoot);
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
      return !myRemoteRoot.isEmpty() && path.startsWith(remotePrefix);
    }

    @Override
    public PathMapping clone() {
      return new PathMapping(myLocalRoot, myRemoteRoot);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PathMapping mapping = (PathMapping)o;

      if (myLocalRoot != null ? !myLocalRoot.equals(mapping.myLocalRoot) : mapping.myLocalRoot != null) return false;
      if (myRemoteRoot != null ? !myRemoteRoot.equals(mapping.myRemoteRoot) : mapping.myRemoteRoot != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myLocalRoot != null ? myLocalRoot.hashCode() : 0;
      result = 31 * result + (myRemoteRoot != null ? myRemoteRoot.hashCode() : 0);
      return result;
    }
  }
}
