/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.impl.local;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CanonicalFileTrie extends CanonicalFileMapper {

  private static final class OriginalWatchPath {
    @NotNull public final String path;
    @NotNull public final MappingType mappingType;

    public OriginalWatchPath(@NotNull String path, @NotNull MappingType mappingType) {
      this.path = path;
      this.mappingType = mappingType;
    }
  }

  private static class TrieNode {
    @NotNull private Map<String, TrieNode> myChildren = Maps.newHashMap();
    @NotNull private Set<OriginalWatchPath> myOriginalWatchPaths = Sets.newHashSet();
    // Only null for root of trie
    @Nullable private String myName;

    /**
     * Only use this to construct the root of the trie
     */
    public TrieNode() {
      this.myName = null;
    }

    private TrieNode(@NotNull String name) {
      this.myName = name;
    }

    @NotNull
    public TrieNode putChild(@NotNull String name) {
      TrieNode child = myChildren.get(name);
      if (child == null) {
        child = new TrieNode(name);
        myChildren.put(name, child);
      }
      return child;
    }

    @Nullable
    public TrieNode getChild(@NotNull String name) {
      return myChildren.get(name);
    }

    public void addMappedPath(@NotNull String path, @NotNull MappingType mappingType) {
      myOriginalWatchPaths.add(new OriginalWatchPath(path, mappingType));
    }

    public Collection<OriginalWatchPath> getMappedPaths() {
      return myOriginalWatchPaths;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TrieNode trieNode = (TrieNode)o;
      return Objects.equal(myChildren, trieNode.myChildren) &&
             Objects.equal(myOriginalWatchPaths, trieNode.myOriginalWatchPaths) &&
             Objects.equal(myName, trieNode.myName);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myChildren, myOriginalWatchPaths, myName);
    }

    @Override
    public String toString() {
      return "TrieNode{" +
             "myName='" + myName + '\'' +
             ", myChildren=" + myChildren +
             ", myOriginalWatchPaths=" + myOriginalWatchPaths +
             '}';
    }
  }

  private final TrieNode root = new TrieNode();
  private final Set<OriginalWatchPath> canonicalWatchRoots = Sets.newHashSet();

  /**
   * @param file
   * @param mappingType
   * @return the canonical path for {@param file}
   */
  @Nullable
  @Override
  public String addMapping(@NotNull File file, @NotNull MappingType mappingType) {
    String canonicalPath = symLinkToRealPath(file);
    String filePath = file.getPath();
    if (FileUtil.comparePaths(canonicalPath, filePath) == 0) {
      canonicalWatchRoots.add(new OriginalWatchPath(filePath, mappingType));
      return canonicalPath;
    }

    if (canonicalPath == null) {
      return null;
    }
    List<String> pathComponents = FileUtil.splitPath(canonicalPath);
    TrieNode currentNode = root;
    for (String pathComponent : pathComponents) {
      currentNode = currentNode.putChild(pathComponent);
    }
    currentNode.addMappedPath(filePath, mappingType);
    return canonicalPath;
  }

  @NotNull
  @Override
  public List<String> getMapping(@NotNull String canonicalPath) {
    List<String> pathComponents = FileUtil.splitPath(canonicalPath);
    TrieNode currentNode = root;
    List<String> mappedPaths = Lists.newArrayList();

    for (int i = 0; i < pathComponents.size(); ++i) {
      String pathComponent = pathComponents.get(i);
      currentNode = currentNode.getChild(pathComponent);
      if (currentNode == null) {
        break;
      }
      for (OriginalWatchPath originalWatchPath : currentNode.getMappedPaths()) {
        // For a flat root, we will match if this is a direct child of the root or if this is the root exactly
        if (originalWatchPath.mappingType == MappingType.RECURSIVE ||
            (i + 1) == (pathComponents.size() - 1)) {
          mappedPaths.add(combine(originalWatchPath.path, pathComponents, i + 1));
        } else if (i == (pathComponents.size() - 1)) {
          mappedPaths.add(originalWatchPath.path);
        }
      }
    }
    if (mappedPaths.size() == 0) {
      return ImmutableList.of(canonicalPath);
    }

    // See if this path is reachable by a canonical path as well as the symlink path we found
    for (OriginalWatchPath originalWatchPath : canonicalWatchRoots) {
      ThreeState ancestorThreeState = FileUtil.isAncestorThreeState(originalWatchPath.path, canonicalPath, false);
      if (ancestorThreeState == ThreeState.YES ||
          (ancestorThreeState == ThreeState.UNSURE && originalWatchPath.mappingType == MappingType.RECURSIVE)) {
        mappedPaths.add(canonicalPath);
        break;
      }
    }

    return mappedPaths;
  }
}
