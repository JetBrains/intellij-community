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
package com.intellij.project.model.impl.library;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.roots.impl.RootProviderBaseImpl;
import com.intellij.openapi.roots.impl.libraries.JarDirectories;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.model.library.JpsOrderRootType;

import java.util.*;

/**
 * @author nik
 */
public class JpsLibraryDelegate implements LibraryEx {
  private final JpsLibrary myJpsLibrary;
  private final JpsLibraryTableImpl myLibraryTable;
  private final Map<OrderRootType, VirtualFilePointerContainer> myRoots;
  private final RootProviderBaseImpl myRootProvider = new MyRootProvider();

  public JpsLibraryDelegate(JpsLibrary library, JpsLibraryTableImpl table) {
    myJpsLibrary = library;
    myLibraryTable = table;
    myRoots = new HashMap<OrderRootType, VirtualFilePointerContainer>();
  }

  @Override
  public String getName() {
    return myJpsLibrary.getName();
  }

  @Override
  public PersistentLibraryKind<?> getKind() {
    return null;
  }

  @Override
  public LibraryProperties getProperties() {
    return null;
  }

  @NotNull
  @Override
  public String[] getUrls(@NotNull OrderRootType rootType) {
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    if (container == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    return container.getUrls();
  }

  @NotNull
  @Override
  public VirtualFile[] getFiles(@NotNull OrderRootType rootType) {
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    if (container == null) return VirtualFile.EMPTY_ARRAY;
    final List<VirtualFile> expanded = new ArrayList<VirtualFile>();
    for (JpsLibraryRoot root : myJpsLibrary.getRoots(getJpsRootType(rootType))) {
      final VirtualFilePointer pointer = container.findByUrl(root.getUrl());
      if (pointer == null) continue;
      VirtualFile file = pointer.getFile();
      if (file == null) continue;

      if (file.isDirectory() && root.getInclusionOptions() != JpsLibraryRoot.InclusionOptions.ROOT_ITSELF) {
        LibraryImpl.collectJarFiles(file, expanded, root.getInclusionOptions() == JpsLibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY);
        continue;
      }
      expanded.add(file);
    }
    return VfsUtilCore.toVirtualFileArray(expanded);
  }

  @Override
  public List<String> getInvalidRootUrls(OrderRootType type) {
    final VirtualFilePointerContainer container = myRoots.get(type);
    if (container == null) return Collections.emptyList();
    final List<VirtualFilePointer> pointers = container.getList();
    List<String> invalidPaths = null;
    for (VirtualFilePointer pointer : pointers) {
      if (!pointer.isValid()) {
        if (invalidPaths == null) {
          invalidPaths = new SmartList<String>();
        }
        invalidPaths.add(pointer.getUrl());
      }
    }
    return invalidPaths == null ? Collections.<String>emptyList() : invalidPaths;
  }

  @Override
  public boolean isDisposed() {
    return false;
  }

  @Override
  public LibraryTable getTable() {
    return myLibraryTable;
  }

  @NotNull
  @Override
  public RootProvider getRootProvider() {
    return myRootProvider;
  }

  @Override
  public void dispose() {
  }

  @Override
  public Library cloneLibrary(RootModelImpl rootModel) {
    throw new UnsupportedOperationException("'cloneLibrary' not implemented in " + getClass().getName());
  }

  @NotNull
  @Override
  public ModifiableModelEx getModifiableModel() {
    throw new UnsupportedOperationException("'getModifiableModel' not implemented in " + getClass().getName());
  }

  @NotNull
  @Override
  public String[] getExcludedRootUrls() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull
  @Override
  public VirtualFile[] getExcludedRoots() {
    return VirtualFile.EMPTY_ARRAY;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isJarDirectory(@NotNull String url) {
    return isJarDirectory(url, JarDirectories.DEFAULT_JAR_DIRECTORY_TYPE);
  }

  @Override
  public boolean isJarDirectory(@NotNull String url, @NotNull OrderRootType rootType) {
    for (JpsLibraryRoot root : myJpsLibrary.getRoots(getJpsRootType(rootType))) {
      if (url.equals(root.getUrl()) && root.getInclusionOptions() != JpsLibraryRoot.InclusionOptions.ROOT_ITSELF) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isValid(@NotNull String url, @NotNull OrderRootType rootType) {
    final VirtualFilePointerContainer container = myRoots.get(rootType);
    if (container == null) return false;
    final VirtualFilePointer fp = container.findByUrl(url);
    return fp != null && fp.isValid();
  }

  private static JpsOrderRootType getJpsRootType(OrderRootType type) {
    if (type == OrderRootType.CLASSES) return JpsOrderRootType.COMPILED;
    if (type == OrderRootType.SOURCES) return JpsOrderRootType.SOURCES;
    if (type == OrderRootType.DOCUMENTATION) return JpsOrderRootType.DOCUMENTATION;
    return JpsOrderRootType.COMPILED;
  }

  private class MyRootProvider extends RootProviderBaseImpl {
    @NotNull
    @Override
    public String[] getUrls(@NotNull OrderRootType rootType) {
      Set<String> originalUrls = new LinkedHashSet<String>(Arrays.asList(JpsLibraryDelegate.this.getUrls(rootType)));
      for (VirtualFile file : getFiles(rootType)) { // Add those expanded with jar directories.
        originalUrls.add(file.getUrl());
      }
      return ArrayUtil.toStringArray(originalUrls);
    }

    @NotNull
    @Override
    public VirtualFile[] getFiles(@NotNull OrderRootType rootType) {
      return JpsLibraryDelegate.this.getFiles(rootType);
    }
  }
}
