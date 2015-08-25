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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author traff
 */
public class PathMappingSettings extends AbstractPathMapper implements Cloneable {

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
        if (m != null && !isAnyEmpty(m.getLocalRoot(), m.getRemoteRoot())) {
          result.add(m);
        }
      }
    }
    return result;
  }

  public PathMappingSettings() {
    myPathMappings = ContainerUtil.newArrayList();
  }

  @NotNull
  static String norm(@NotNull String path) {
    return FileUtil.toSystemIndependentName(path);
  }

  @NotNull
  private static String normLocal(@NotNull String path) {
    if (SystemInfo.isWindows) {
      path = path.toLowerCase();
    }

    return norm(path);
  }

  @Override
  public boolean isEmpty() {
    return myPathMappings.isEmpty();
  }

  /**
   * @deprecated use {@code !isEmpty()} instead
   * @see #isEmpty()
   */
  @Deprecated
  public boolean isUseMapping() {
    return !isEmpty();
  }

  public static class BestMappingSelector {
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
  @Override
  public String convertToLocal(@NotNull String remotePath) {
    String localPath = convertToLocal(remotePath, myPathMappings);
    return localPath != null ? localPath : remotePath;
  }

  @NotNull
  @Override
  public String convertToRemote(@NotNull String localPath) {
    String remotePath = convertToRemote(localPath, myPathMappings);
    return remotePath != null ? remotePath : localPath;
  }

  public void add(@NotNull PathMapping mapping) {
    if (isAnyEmpty(mapping.getLocalRoot(), mapping.getRemoteRoot())) {
      return;
    }
    myPathMappings.add(mapping);
  }

  public void addMapping(@Nullable String local, @Nullable String remote) {
    PathMapping mapping = new PathMapping(local, remote);
    add(mapping);
  }

  public void addMappingCheckUnique(@NotNull String local, @NotNull String remote) {
    for (PathMapping mapping : myPathMappings) {
      if (pathEquals(local, mapping.getLocalRoot()) && pathEquals(remote, mapping.getRemoteRoot())) {
        return;
      }
    }
    addMapping(local, remote);
  }

  private static boolean pathEquals(@NotNull String path1, @NotNull String path2) {
    return norm(path1).equals(norm(path2));
  }

  @Override
  @NotNull
  protected final Collection<PathMapping> getAvailablePathMappings() {
    return Collections.unmodifiableCollection(myPathMappings);
  }

  @NotNull
  public List<PathMapping> getPathMappings() {
    return myPathMappings;
  }

  public void setPathMappings(@Nullable final List<PathMapping> pathMappings) {
    myPathMappings = create(pathMappings);
  }

  @NotNull
  public static String mapToLocal(@NotNull String path, @Nullable String remoteRoot, @Nullable String localRoot) {
    if (isAnyEmpty(localRoot, remoteRoot)) {
      return path;
    }
    path = norm(path);
    String remotePrefix = norm(remoteRoot);
    path = path.replace(remotePrefix, norm(localRoot));
    return path;
  }

  @Contract(value = "null, _ -> true; _, null -> true", pure = true)
  public static boolean isAnyEmpty(@Nullable String localRoot, @Nullable String remoteRoot) {
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
    if (element == null || mappings == null || mappings.isEmpty()) {
      return;
    }
    element.addContent(XmlSerializer.serialize(mappings));
  }

  public void addAll(@NotNull PathMappingSettings settings) {
    myPathMappings.addAll(settings.getPathMappings());
  }

  public void addAll(@NotNull List<PathMapping> mappings) {
    myPathMappings.addAll(mappings);
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

    public PathMapping(@Nullable String localRoot, @Nullable String remoteRoot) {
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

    public int getLocalLen() {
      return myLocalRoot != null ? myLocalRoot.length() : -1;
    }

    public int getRemoteLen() {
      return myRemoteRoot != null ? myRemoteRoot.length() : -1;
    }

    public void setLocalRoot(@Nullable String localRoot) {
      myLocalRoot = normalize(localRoot);
    }

    public void setRemoteRoot(@Nullable String remoteRoot) {
      myRemoteRoot = normalize(remoteRoot);
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
      return isAnyEmpty(myLocalRoot, myRemoteRoot);
    }

    private static String trimSlash(@NotNull String s) {
      if (s.equals("/")) {
        return s;
      }
      return StringUtil.trimEnd(s, "/");
    }

    public boolean canReplaceRemote(@NotNull String path) {
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
