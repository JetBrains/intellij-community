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
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
public class ProjectRootContainerImpl implements JDOMExternalizable, ProjectRootContainer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectRootContainerImpl");
  private final Map<OrderRootType, CompositeProjectRoot> myRoots = new THashMap<>();
  private final Map<OrderRootType, VirtualFile[]> myCachedFiles = new THashMap<>();

  private boolean myInsideChange;
  private final List<ProjectRootListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private final boolean myNoCopyJars;

  ProjectRootContainerImpl(boolean noCopyJars) {
    myNoCopyJars = noCopyJars;

    for (OrderRootType rootType : OrderRootType.getAllTypes()) {
      myRoots.put(rootType, new CompositeProjectRoot());
      myCachedFiles.put(rootType, VirtualFile.EMPTY_ARRAY);
    }
  }

  @Override
  @NotNull
  public VirtualFile[] getRootFiles(@NotNull OrderRootType type) {
    return ObjectUtils.chooseNotNull(myCachedFiles.get(type), VirtualFile.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public ProjectRoot[] getRoots(@NotNull OrderRootType type) {
    return myRoots.get(type).getProjectRoots();
  }

  void startChange() {
    myInsideChange = true;  // argh!! has to have this abomination just because of horrible Sdk.getSdkModificator()/commitChanges() are separated
  }

  private void assertNotInsideChange() {
    if (myInsideChange) throw new IllegalStateException();
  }
  private void assertInsideChange() {
    if (!myInsideChange) throw new IllegalStateException();
  }

  @Override
  public void changeRoots(@NotNull Runnable change) {
    assertNotInsideChange();
    myInsideChange = true;
    Map<OrderRootType, VirtualFile[]> oldRoots = new THashMap<>(myCachedFiles);

    try {
      change.run();
    }
    finally {
      myInsideChange = false;

      if (cacheFiles(oldRoots)) {
        fireRootsChanged();
      }
    }
  }


  private boolean cacheFiles(@NotNull Map<OrderRootType, VirtualFile[]> oldRoots) {
    myCachedFiles.clear();

    boolean changed = false;
    for (OrderRootType orderRootType : OrderRootType.getAllTypes()) {
      final VirtualFile[] roots = myRoots.get(orderRootType).getVirtualFiles();
      changed |= !Comparing.equal(roots, oldRoots.get(orderRootType));
      myCachedFiles.put(orderRootType, roots);
    }
    return changed;
  }

  void addProjectRootContainerListener(@NotNull ProjectRootListener listener) {
    myListeners.add(listener);
  }

  public void removeProjectRootContainerListener(@NotNull ProjectRootListener listener) {
    myListeners.remove(listener);
  }

  private void fireRootsChanged() {
    for (final ProjectRootListener listener : myListeners) {
      listener.rootsChanged();
    }
  }

  @Override
  public void removeRoot(@NotNull ProjectRoot root, @NotNull OrderRootType type) {
    assertInsideChange();
    myRoots.get(type).remove(root);
  }

  @Override
  @NotNull
  public ProjectRoot addRoot(@NotNull VirtualFile virtualFile, @NotNull OrderRootType type) {
    assertInsideChange();
    return myRoots.get(type).add(virtualFile);
  }

  @Override
  public void addRoot(@NotNull ProjectRoot root, @NotNull OrderRootType type) {
    assertInsideChange();
    myRoots.get(type).add(root);
  }

  @Override
  public void removeAllRoots(@NotNull OrderRootType type) {
    assertInsideChange();
    myRoots.get(type).clear();
  }

  @Override
  public void removeRoot(@NotNull VirtualFile root, @NotNull OrderRootType type) {
    assertInsideChange();
    myRoots.get(type).remove(root);
  }

  @Override
  public void removeAllRoots() {
    assertInsideChange();
    for (CompositeProjectRoot myRoot : myRoots.values()) {
      myRoot.clear();
    }
  }

  @Override
  public void update() {
    assertInsideChange();
    for (CompositeProjectRoot myRoot : myRoots.values()) {
      myRoot.update();
    }
  }

  @Override
  public void readExternal(Element element) {
    assertInsideChange();
    for (PersistentOrderRootType type : OrderRootType.getAllPersistentTypes()) {
      read(element, type);
    }

    ApplicationManager.getApplication().runReadAction(() -> {
      myRoots.values().forEach(root -> {
        if (myNoCopyJars) {
          setNoCopyJars(root);
        }
      });
      cacheFiles(new THashMap<>(myCachedFiles));
    });

    for (OrderRootType type : OrderRootType.getAllTypes()) {
      if (myRoots.get(type) == null) {
        LOG.error(type + " wasn't serialized");
        myRoots.put(type, new CompositeProjectRoot());
      }

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

  void copyRootsFrom(@NotNull ProjectRootContainerImpl rootContainer) {
    changeRoots(() -> {
      removeAllRoots();
      for (OrderRootType rootType : OrderRootType.getAllTypes()) {
        final ProjectRoot[] newRoots = rootContainer.getRoots(rootType);
        for (ProjectRoot newRoot : newRoots) {
          addRoot(newRoot, rootType);
        }
      }
    });
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
    if (children.size() != 1) {
      LOG.error(children);
    }
    CompositeProjectRoot root = (CompositeProjectRoot)CompositeProjectRoot.read(children.get(0));
    myRoots.put(type, root);
  }

  private void write(Element roots, PersistentOrderRootType type) {
    String sdkRootName = type.getSdkRootName();
    if (sdkRootName != null) {
      Element e = new Element(sdkRootName);
      roots.addContent(e);
      final Element root = CompositeProjectRoot.write(myRoots.get(type));
      e.addContent(root);
    }
  }
}
