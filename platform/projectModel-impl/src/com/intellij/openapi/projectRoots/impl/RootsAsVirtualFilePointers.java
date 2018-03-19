// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.PersistentOrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtilRt;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
public class RootsAsVirtualFilePointers implements RootProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectRootContainerImpl");
  private final Map<OrderRootType, VirtualFilePointerContainer> myRoots = new THashMap<>();

  private final boolean myNoCopyJars;

  RootsAsVirtualFilePointers(boolean noCopyJars, VirtualFilePointerListener listener, @NotNull Disposable parent) {
    myNoCopyJars = noCopyJars;

    for (OrderRootType rootType : OrderRootType.getAllTypes()) {
      myRoots.put(rootType, VirtualFilePointerManager.getInstance().createContainer(parent, listener));
    }
  }

  @Override
  @NotNull
  public VirtualFile[] getFiles(@NotNull OrderRootType type) {
    VirtualFilePointerContainer container = myRoots.get(type);
    return container == null ? VirtualFile.EMPTY_ARRAY : container.getFiles();
  }

  @Override
  @NotNull
  public String[] getUrls(@NotNull OrderRootType type) {
    VirtualFilePointerContainer container = myRoots.get(type);
    return container == null ? ArrayUtilRt.EMPTY_STRING_ARRAY : container.getUrls();
  }

  public void addRoot(@NotNull VirtualFile virtualFile, @NotNull OrderRootType type) {
    myRoots.get(type).add(virtualFile);
  }

  public void addRoot(@NotNull String url, @NotNull OrderRootType type) {
    myRoots.get(type).add(url);
  }

  public void removeAllRoots(@NotNull OrderRootType type) {
    myRoots.get(type).clear();
  }

  public void removeRoot(@NotNull VirtualFile root, @NotNull OrderRootType type) {
    removeRoot(root.getUrl(), type);
  }

  public void removeRoot(@NotNull String url, @NotNull OrderRootType type) {
    VirtualFilePointerContainer container = myRoots.get(type);
    VirtualFilePointer pointer = container.findByUrl(url);
    if (pointer != null) {
      container.remove(pointer);
    }
  }

  public void removeAllRoots() {
    for (VirtualFilePointerContainer myRoot : myRoots.values()) {
      myRoot.clear();
    }
  }

  public void readExternal(@NotNull Element element) {
    for (PersistentOrderRootType type : OrderRootType.getAllPersistentTypes()) {
      read(element, type);
    }

    ApplicationManager.getApplication().runReadAction(() -> myRoots.values().forEach(container -> {
      if (myNoCopyJars) {
        for (String root : container.getUrls()) {
          setNoCopyJars(root);
        }
      }
    }));

    for (OrderRootType type : OrderRootType.getAllTypes()) {
      if (myRoots.get(type) == null) {
        LOG.error(type + " wasn't serialized");
      }
    }
  }

  public void writeExternal(@NotNull Element element) {
    List<PersistentOrderRootType> allTypes = OrderRootType.getSortedRootTypes();
    for (PersistentOrderRootType type : allTypes) {
      write(element, type);
    }
  }

  void copyRootsFrom(@NotNull RootProvider rootContainer) {
    removeAllRoots();
    for (OrderRootType rootType : OrderRootType.getAllTypes()) {
      final String[] newRoots = rootContainer.getUrls(rootType);
      for (String newRoot : newRoots) {
        addRoot(newRoot, rootType);
      }
    }
  }

  private static void setNoCopyJars(@NotNull String url) {
    if (StandardFileSystems.JAR_PROTOCOL.equals(VirtualFileManager.extractProtocol(url))) {
      String path = VirtualFileManager.extractPath(url);
      final VirtualFileSystem fileSystem = StandardFileSystems.jar();
      if (fileSystem instanceof JarCopyingFileSystem) {
        ((JarCopyingFileSystem)fileSystem).setNoCopyJarForPath(path);
      }
    }
  }

  /**
   <roots>
   <sourcePath>
     <root type="composite">
       <root type="simple" url="jar://I:/Java/jdk1.8/src.zip!/" />
       <root type="simple" url="jar://I:/Java/jdk1.8/javafx-src.zip!/" />
     </root>
   </sourcePath>
   </roots>
   */
  private void read(@NotNull Element roots, @NotNull PersistentOrderRootType type)  {
    String sdkRootName = type.getSdkRootName();
    Element child = sdkRootName == null ? null : roots.getChild(sdkRootName);
    if (child == null) {
      return;
    }

    List<Element> composites = child.getChildren();
    if (composites.size() != 1) {
      LOG.error(composites);
    }
    Element composite = composites.get(0);

    VirtualFilePointerContainer container = myRoots.get(type);
    assert container != null : "unknown root type: " + type;
    container.readExternal(composite, "root", false);
  }

  /**
   <roots>
   <sourcePath>
     <root type="composite">
       <root type="simple" url="jar://I:/Java/jdk1.8/src.zip!/" />
       <root type="simple" url="jar://I:/Java/jdk1.8/javafx-src.zip!/" />
     </root>
   </sourcePath>
   </roots>
   */
  private void write(@NotNull Element roots, @NotNull PersistentOrderRootType type) {
    String sdkRootName = type.getSdkRootName();
    if (sdkRootName == null) {
      return;
    }
    Element e = new Element(sdkRootName);
    roots.addContent(e);
    Element composite = new Element("root");
    composite.setAttribute("type", "composite");
    e.addContent(composite);
    myRoots.get(type).writeExternal(composite, "root", false);
    for (Element root : composite.getChildren()) {
      root.setAttribute("type", "simple");
    }
  }

  @Override
  public void addRootSetChangedListener(@NotNull RootSetChangedListener listener) {
    throw new RuntimeException();
  }

  @Override
  public void addRootSetChangedListener(@NotNull RootSetChangedListener listener, @NotNull Disposable parentDisposable) {
    throw new RuntimeException();
  }

  @Override
  public void removeRootSetChangedListener(@NotNull RootSetChangedListener listener) {
    throw new RuntimeException();
  }
}
