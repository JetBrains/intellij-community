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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerContainer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class JavaModuleExternalPathsImpl extends JavaModuleExternalPaths {
  @NonNls private static final String ROOT_ELEMENT = JpsJavaModelSerializerExtension.ROOT_TAG;
  private final Map<OrderRootType, VirtualFilePointerContainer> myOrderRootPointerContainers = new HashMap<>();
  private JavaModuleExternalPathsImpl mySource;

  public JavaModuleExternalPathsImpl() {
  }

  public JavaModuleExternalPathsImpl(JavaModuleExternalPathsImpl source) {
    mySource = source;
    copyContainersFrom(source);
  }

  @Override
  public ModuleExtension getModifiableModel(boolean writable) {
    return new JavaModuleExternalPathsImpl(this);
  }

  @Override
  public void commit() {
    mySource.copyContainersFrom(this);
  }

  @NotNull
  @Override
  public String[] getJavadocUrls() {
    final VirtualFilePointerContainer container = myOrderRootPointerContainers.get(JavadocOrderRootType.getInstance());
    return container != null ? container.getUrls() : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull
  @Override
  public VirtualFile[] getExternalAnnotationsRoots() {
    final VirtualFilePointerContainer container = myOrderRootPointerContainers.get(AnnotationOrderRootType.getInstance());
    return container != null ? container.getFiles() : VirtualFile.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public String[] getExternalAnnotationsUrls() {
    final VirtualFilePointerContainer container = myOrderRootPointerContainers.get(AnnotationOrderRootType.getInstance());
    return container != null ? container.getUrls() : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public void setJavadocUrls(@NotNull String[] urls) {
    setRootUrls(JavadocOrderRootType.getInstance(), urls);
  }

  @Override
  public void setExternalAnnotationUrls(@NotNull String[] urls) {
    setRootUrls(AnnotationOrderRootType.getInstance(), urls);
  }

  private void setRootUrls(final OrderRootType orderRootType, @NotNull final String[] urls) {
    VirtualFilePointerContainer container = myOrderRootPointerContainers.get(orderRootType);
    if (container == null) {
      container = VirtualFilePointerManager.getInstance().createContainer(this, null);
      myOrderRootPointerContainers.put(orderRootType, container);
    }
    container.clear();
    for (final String url : urls) {
      container.add(url);
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    for (PersistentOrderRootType orderRootType : OrderRootType.getAllPersistentTypes()) {
      String paths = orderRootType.getModulePathsName();
      if (paths != null) {
        final Element pathsElement = element.getChild(paths);
        if (pathsElement != null) {
          VirtualFilePointerContainer container = VirtualFilePointerManager.getInstance().createContainer(this, null);
          myOrderRootPointerContainers.put(orderRootType, container);
          container.readExternal(pathsElement, ROOT_ELEMENT);
        }
      }
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    for (OrderRootType orderRootType : myOrderRootPointerContainers.keySet()) {
      VirtualFilePointerContainer container = myOrderRootPointerContainers.get(orderRootType);
      if (container != null && container.size() > 0) {
        final Element javaDocPaths = new Element(((PersistentOrderRootType)orderRootType).getModulePathsName());
        container.writeExternal(javaDocPaths, ROOT_ELEMENT);
        element.addContent(javaDocPaths);
      }
    }
  }

  private void copyContainersFrom(@NotNull JavaModuleExternalPathsImpl paths) {
    myOrderRootPointerContainers.clear();
    for (PersistentOrderRootType orderRootType : OrderRootType.getAllPersistentTypes()) {
      final VirtualFilePointerContainer otherContainer = paths.myOrderRootPointerContainers.get(orderRootType);
      if (otherContainer != null) {
        myOrderRootPointerContainers.put(orderRootType, otherContainer.clone(this, null));
      }
    }
  }

  @Override
  public boolean isChanged() {
    if (myOrderRootPointerContainers.size() != mySource.myOrderRootPointerContainers.size()) return true;
    for (final OrderRootType type : myOrderRootPointerContainers.keySet()) {
      final VirtualFilePointerContainer container = myOrderRootPointerContainers.get(type);
      final VirtualFilePointerContainer otherContainer = mySource.myOrderRootPointerContainers.get(type);
      if (container == null || otherContainer == null) {
        if (container != otherContainer) return true;
      }
      else {
        final String[] urls = container.getUrls();
        final String[] otherUrls = otherContainer.getUrls();
        if (urls.length != otherUrls.length) return true;
        for (int i = 0; i < urls.length; i++) {
          if (!Comparing.strEqual(urls[i], otherUrls[i])) return true;
        }
      }
    }
    return false;
  }

  @Override
  public void dispose() {
  }
}
