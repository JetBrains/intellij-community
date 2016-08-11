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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.ProjectRootListener;
import com.intellij.openapi.projectRoots.ex.ProjectRoot;
import com.intellij.openapi.projectRoots.ex.ProjectRootContainer;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.PersistentOrderRootType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.vfs.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
public class ProjectRootContainerImpl implements JDOMExternalizable, ProjectRootContainer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectRootContainerImpl");
  private final Map<OrderRootType, CompositeProjectRoot> myRoots = new HashMap<>();

  private Map<OrderRootType, VirtualFile[]> myFiles = new HashMap<>();

  private boolean myInsideChange = false;
  private final List<ProjectRootListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean myNoCopyJars = false;

  public ProjectRootContainerImpl(boolean noCopyJars) {
    myNoCopyJars = noCopyJars;

    for (OrderRootType rootType : OrderRootType.getAllTypes()) {
      myRoots.put(rootType, new CompositeProjectRoot());
      myFiles.put(rootType, VirtualFile.EMPTY_ARRAY);
    }
  }

  @Override
  @NotNull
  public VirtualFile[] getRootFiles(@NotNull OrderRootType type) {
    return ObjectUtils.chooseNotNull(myFiles.get(type), VirtualFile.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public ProjectRoot[] getRoots(@NotNull OrderRootType type) {
    return myRoots.get(type).getProjectRoots();
  }

  @Override
  public void startChange() {
    LOG.assertTrue(!myInsideChange);

    myInsideChange = true;
  }

  @Override
  public void finishChange() {
    LOG.assertTrue(myInsideChange);
    HashMap<OrderRootType, VirtualFile[]> oldRoots = new HashMap<>(myFiles);

    boolean changes = false;
    for (OrderRootType orderRootType : OrderRootType.getAllTypes()) {
      final VirtualFile[] roots = myRoots.get(orderRootType).getVirtualFiles();
      changes = changes || !Comparing.equal(roots, oldRoots.get(orderRootType));
      myFiles.put(orderRootType, roots);
    }
    if (changes) {
      fireRootsChanged();
    }

    myInsideChange = false;
  }

  public void addProjectRootContainerListener(ProjectRootListener listener) {
    myListeners.add(listener);
  }

  public void removeProjectRootContainerListener(ProjectRootListener listener) {
    myListeners.remove(listener);
  }

  private void fireRootsChanged() {
    for (final ProjectRootListener listener : myListeners) {
      listener.rootsChanged();
    }
  }


  @Override
  public void removeRoot(@NotNull ProjectRoot root, @NotNull OrderRootType type) {
    LOG.assertTrue(myInsideChange);
    myRoots.get(type).remove(root);
  }

  @Override
  @NotNull
  public ProjectRoot addRoot(@NotNull VirtualFile virtualFile, @NotNull OrderRootType type) {
    LOG.assertTrue(myInsideChange);
    return myRoots.get(type).add(virtualFile);
  }

  @Override
  public void addRoot(@NotNull ProjectRoot root, @NotNull OrderRootType type) {
    LOG.assertTrue(myInsideChange);
    myRoots.get(type).add(root);
  }

  @Override
  public void removeAllRoots(@NotNull OrderRootType type) {
    LOG.assertTrue(myInsideChange);
    myRoots.get(type).clear();
  }

  @Override
  public void removeRoot(@NotNull VirtualFile root, @NotNull OrderRootType type) {
    LOG.assertTrue(myInsideChange);
    myRoots.get(type).remove(root);
  }

  @Override
  public void removeAllRoots() {
    LOG.assertTrue(myInsideChange);
    for (CompositeProjectRoot myRoot : myRoots.values()) {
      myRoot.clear();
    }
  }

  @Override
  public void update() {
    LOG.assertTrue(myInsideChange);
    for (CompositeProjectRoot myRoot : myRoots.values()) {
      myRoot.update();
    }
  }

  @Override
  public void readExternal(Element element) {
    for (PersistentOrderRootType type : OrderRootType.getAllPersistentTypes()) {
      read(element, type);
    }

    ApplicationManager.getApplication().runReadAction(() -> {
      myFiles = new HashMap<>();
      for (OrderRootType rootType : myRoots.keySet()) {
        CompositeProjectRoot root = myRoots.get(rootType);
        if (myNoCopyJars) {
          setNoCopyJars(root);
        }
        myFiles.put(rootType, root.getVirtualFiles());
      }
    });

    for (OrderRootType type : OrderRootType.getAllTypes()) {
      final VirtualFile[] newRoots = getRootFiles(type);
      final VirtualFile[] oldRoots = VirtualFile.EMPTY_ARRAY;
      if (!Comparing.equal(oldRoots, newRoots)) {
        fireRootsChanged();
        break;
      }
    }
  }

  @Override
  public void writeExternal(Element element) {
    List<PersistentOrderRootType> allTypes = OrderRootType.getSortedRootTypes();
    for (PersistentOrderRootType type : allTypes) {
      write(element, type);
    }
  }

  private static void setNoCopyJars(ProjectRoot root) {
    if (root instanceof SimpleProjectRoot) {
      String url = ((SimpleProjectRoot)root).getUrl();
      if (StandardFileSystems.JAR_PROTOCOL.equals(VirtualFileManager.extractProtocol(url))) {
        String path = VirtualFileManager.extractPath(url);
        final VirtualFileSystem fileSystem = StandardFileSystems.jar();
        if (fileSystem instanceof JarCopyingFileSystem) {
          ((JarCopyingFileSystem)fileSystem).setNoCopyJarForPath(path);
        }
      }
    }
    else if (root instanceof CompositeProjectRoot) {
      ProjectRoot[] roots = ((CompositeProjectRoot)root).getProjectRoots();
      for (ProjectRoot root1 : roots) {
        setNoCopyJars(root1);
      }
    }
  }

  private void read(Element element, PersistentOrderRootType type)  {
    String sdkRootName = type.getSdkRootName();
    Element child = sdkRootName != null ? element.getChild(sdkRootName) : null;
    if (child == null) {
      myRoots.put(type, new CompositeProjectRoot());
      return;
    }

    List<Element> children = child.getChildren();
    LOG.assertTrue(children.size() == 1);
    CompositeProjectRoot root = (CompositeProjectRoot)ProjectRootUtil.read(children.get(0));
    myRoots.put(type, root);
  }

  private void write(Element roots, PersistentOrderRootType type) {
    String sdkRootName = type.getSdkRootName();
    if (sdkRootName != null) {
      Element e = new Element(sdkRootName);
      roots.addContent(e);
      final Element root = ProjectRootUtil.write(myRoots.get(type));
      if (root != null) {
        e.addContent(root);
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  void readOldVersion(Element child) {
    for (Element root : child.getChildren("root")) {
      String url = root.getAttributeValue("file");
      SimpleProjectRoot projectRoot = new SimpleProjectRoot(url);
      String type = root.getChild("property").getAttributeValue("value");

      for (PersistentOrderRootType rootType : OrderRootType.getAllPersistentTypes()) {
        if (type.equals(rootType.getOldSdkRootName())) {
          addRoot(projectRoot, rootType);
          break;
        }
      }
    }

    myFiles = new HashMap<>();
    for (OrderRootType rootType : myRoots.keySet()) {
      myFiles.put(rootType, myRoots.get(rootType).getVirtualFiles());
    }
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      final VirtualFile[] oldRoots = VirtualFile.EMPTY_ARRAY;
      final VirtualFile[] newRoots = getRootFiles(type);
      if (!Comparing.equal(oldRoots, newRoots)) {
        fireRootsChanged();
        break;
      }
    }
  }
}
