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

package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.ex.ProjectRoot;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author mike
 */
class CompositeProjectRoot implements ProjectRoot {
  @NonNls private static final String SIMPLE_ROOT = "simple";
  @NonNls private static final String COMPOSITE_ROOT = "composite";
  @NonNls private static final String ATTRIBUTE_TYPE = "type";
  @NonNls private static final String ELEMENT_ROOT = "root";
  private final List<ProjectRoot> myRoots = new ArrayList<>();

  @NotNull 
  ProjectRoot[] getProjectRoots() {
    return myRoots.toArray(new ProjectRoot[myRoots.size()]);
  }

  @Override
  @NotNull
  public String getPresentableString() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public VirtualFile[] getVirtualFiles() {
    List<VirtualFile> result = new ArrayList<>();
    for (ProjectRoot root : myRoots) {
      ContainerUtil.addAll(result, root.getVirtualFiles());
    }

    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  @NotNull
  public String[] getUrls() {
    final List<String> result = new ArrayList<>();
    for (ProjectRoot root : myRoots) {
      ContainerUtil.addAll(result, root.getUrls());
    }
    return ArrayUtil.toStringArray(result);
  }

  @Override
  public boolean isValid() {
    return true;
  }

  void remove(@NotNull ProjectRoot root) {
    myRoots.remove(root);
  }

  @NotNull
  ProjectRoot add(@NotNull VirtualFile virtualFile) {
    final SimpleProjectRoot root = new SimpleProjectRoot(virtualFile);
    myRoots.add(root);
    return root;
  }

  void add(@NotNull ProjectRoot root) {
    myRoots.add(root);
  }

  void remove(@NotNull VirtualFile root) {
    for (Iterator<ProjectRoot> iterator = myRoots.iterator(); iterator.hasNext();) {
      ProjectRoot projectRoot = iterator.next();
      if (projectRoot instanceof SimpleProjectRoot) {
        SimpleProjectRoot r = (SimpleProjectRoot)projectRoot;
        if (root.equals(r.getFile())) {
          iterator.remove();
        }
      }
    }
  }

  void clear() {
    myRoots.clear();
  }

  public void readExternal(Element element) {
    for (Element child : element.getChildren()) {
      myRoots.add(read(child));
    }
  }

  public void writeExternal(Element element) {
    for (ProjectRoot root : myRoots) {
      Element e = write(root);
      element.addContent(e);
    }
  }

  @Override
  public void update() {
    for (ProjectRoot root : myRoots) {
      root.update();
    }
  }

  @NotNull
  static ProjectRoot read(Element element)  {
    final String type = element.getAttributeValue(ATTRIBUTE_TYPE);

    if (type.equals(SIMPLE_ROOT)) {
      return new SimpleProjectRoot(element);
    }
    if (type.equals(COMPOSITE_ROOT)) {
      CompositeProjectRoot root = new CompositeProjectRoot();
      root.readExternal(element);
      return root;
    }
    throw new IllegalArgumentException("Wrong type: " + type);
  }

  @NotNull
  static Element write(ProjectRoot projectRoot)  {
    Element element = new Element(ELEMENT_ROOT);
    if (projectRoot instanceof SimpleProjectRoot) {
      element.setAttribute(ATTRIBUTE_TYPE, SIMPLE_ROOT);
      ((SimpleProjectRoot)projectRoot).writeExternal(element);
    }
    else if (projectRoot instanceof CompositeProjectRoot) {
      element.setAttribute(ATTRIBUTE_TYPE, COMPOSITE_ROOT);
      ((CompositeProjectRoot)projectRoot).writeExternal(element);
    }
    else {
      throw new IllegalArgumentException("Wrong root: " + projectRoot);
    }

    return element;
  }
}
