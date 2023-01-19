// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

public class PathMappingSettings extends AbstractPathMapper implements Cloneable {
  // C:\
  private static final Pattern WIN_DRIVE = Pattern.compile("^[a-z]:[/\\\\]$", Pattern.CASE_INSENSITIVE);

  @NotNull
  private List<PathMapping> myPathMappings;

  public PathMappingSettings(@Nullable final List<? extends PathMapping> pathMappings) {
    myPathMappings = create(pathMappings);
  }

  @NotNull
  private static List<PathMapping> create(@Nullable final List<? extends PathMapping> mappings) {
    List<PathMapping> result = new ArrayList<>();
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
    myPathMappings = new ArrayList<>();
  }

  @NotNull
  static String norm(@NotNull String path) {
    return FileUtil.toSystemIndependentName(path);
  }

  @NotNull
  private static String normLocal(@NotNull String path) {
    if (SystemInfo.isWindows) {
      path = StringUtil.toLowerCase(path);
    }

    return norm(path);
  }

  @Override
  public boolean isEmpty() {
    return myPathMappings.isEmpty();
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

  public void add(@Nullable PathMapping mapping) {
    if (mapping == null) {
      return;
    }
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
    PathMapping mapping = new PathMapping(local, remote);
    if (myPathMappings.contains(mapping)) return;
    add(mapping);
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

  public void setPathMappings(@Nullable final List<? extends PathMapping> pathMappings) {
    myPathMappings = create(pathMappings);
  }

  @NotNull
  public static String mapToLocal(@NotNull String path, @Nullable String remoteRoot, @Nullable String localRoot) {
    if (isAnyEmpty(localRoot, remoteRoot)) {
      return path;
    }
    path = norm(path);
    String remotePrefix = norm(remoteRoot);
    if (canReplaceRemote(path, remotePrefix)) {
      String left = norm(localRoot);
      String right = path.substring(remotePrefix.length());
      // Left and right part must be separated
      if ((left.endsWith("/") || left.endsWith("\\") || right.startsWith("/") || right.startsWith("\\") ||
           StringUtil.isEmpty(left) || StringUtil.isEmpty(right))) {
        path = left + right;
      }
      else {
        path = left + "/" + right;
      }
    }
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

  public void addAll(@NotNull List<? extends PathMapping> mappings) {
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
      if (localPrefix.isEmpty()) {
        return false;
      }
      final String localPath = normLocal(path);
      final int prefixLength = localPrefix.length();
      return localPath.startsWith(localPrefix) && (localPath.length() == prefixLength || localPath.charAt(prefixLength) == '/');
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
      if (WIN_DRIVE.matcher(s).matches()) {
        // No need to convert c:\ -> C:
        // Path.ancestor doens't work with it
        return s;
      }
      if (s.equals("/")) {
        return s;
      }
      return StringUtil.trimEnd(s, "/");
    }

    public boolean canReplaceRemote(@NotNull String path) {
      if (isEmpty()) {
        return false;
      }

      return PathMappingSettings.canReplaceRemote(path, myRemoteRoot);
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

      if (!Objects.equals(myLocalRoot, mapping.myLocalRoot)) return false;
      if (!Objects.equals(myRemoteRoot, mapping.myRemoteRoot)) return false;

      return true;
    }

    @Override
    public String toString() {
      return "{'" + myLocalRoot + "' <=> '" + myRemoteRoot + "'}";
    }

    @Override
    public int hashCode() {
      int result = myLocalRoot != null ? myLocalRoot.hashCode() : 0;
      result = 31 * result + (myRemoteRoot != null ? myRemoteRoot.hashCode() : 0);
      return result;
    }
  }

  private static boolean canReplaceRemote(@NotNull String path, @NotNull String remotePrefix) {
    path = norm(path);
    remotePrefix = norm(remotePrefix);
    return path.startsWith(remotePrefix) &&
           (path.length() == remotePrefix.length() || remotePrefix.endsWith("/") || path.substring(remotePrefix.length()).startsWith("/"));
  }

  @Override
  public String toString() {
    return "PathMappingSettings{" +
           "myPathMappings=" + myPathMappings +
           "} " + super.toString();
  }
}
