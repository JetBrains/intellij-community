/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.PersistentOrderRootType;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.util.containers.MultiMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JarDirectories implements JDOMExternalizable {
  private final MultiMap<OrderRootType, String> myDirectories = new MultiMap<>();
  private final MultiMap<OrderRootType, String> myRecursivelyIncluded = new MultiMap<>();

  @NonNls private static final String JAR_DIRECTORY_ELEMENT = "jarDirectory";
  @NonNls private static final String URL_ATTR = "url";
  @NonNls private static final String RECURSIVE_ATTR = "recursive";
  @NonNls private static final String ROOT_TYPE_ATTR = "type";
  public static final OrderRootType DEFAULT_JAR_DIRECTORY_TYPE = OrderRootType.CLASSES;

  public void copyFrom(JarDirectories other) {
    myDirectories.clear();
    myDirectories.putAllValues(other.myDirectories);
    myRecursivelyIncluded.clear();
    myRecursivelyIncluded.putAllValues(other.myRecursivelyIncluded);
  }

  public boolean contains(OrderRootType rootType, String url) {
    return myDirectories.get(rootType).contains(url);
  }

  public boolean isRecursive(OrderRootType rootType, String url) {
    return myRecursivelyIncluded.get(rootType).contains(url);
  }

  public void add(OrderRootType rootType, String url, boolean recursively) {
    myDirectories.putValue(rootType, url);
    if (recursively) {
      myRecursivelyIncluded.putValue(rootType, url);
    }
  }

  public void remove(OrderRootType rootType, String url) {
    myDirectories.remove(rootType, url);
    myRecursivelyIncluded.remove(rootType, url);
  }

  public void clear() {
    myDirectories.clear();
    myRecursivelyIncluded.clear();
  }

  public Collection<OrderRootType> getRootTypes() {
    return myDirectories.keySet();
  }

  public Collection<String> getDirectories(OrderRootType rootType) {
    return myDirectories.get(rootType);
  }

  public Collection<? extends String> getAllDirectories() {
    return myDirectories.values();
  }

  public boolean isEmpty() {
    return myDirectories.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JarDirectories)) return false;

    JarDirectories that = (JarDirectories)o;
    return myDirectories.equals(that.myDirectories) && myRecursivelyIncluded.equals(that.myRecursivelyIncluded);
  }

  @Override
  public int hashCode() {
    return 31 * myDirectories.hashCode() + myRecursivelyIncluded.hashCode();
  }

  @Override
  public String toString() {
    return "JAR dirs: " + myDirectories.values();
  }


  @Override
  public void readExternal(Element element) throws InvalidDataException {
    clear();
    final List<Element> jarDirs = element.getChildren(JAR_DIRECTORY_ELEMENT);
    for (Element jarDir : jarDirs) {
      final String url = jarDir.getAttributeValue(URL_ATTR);
      final String recursive = jarDir.getAttributeValue(RECURSIVE_ATTR);
      final OrderRootType rootType = getJarDirectoryRootType(jarDir.getAttributeValue(ROOT_TYPE_ATTR));
      if (url != null) {
        add(rootType, url, Boolean.valueOf(Boolean.parseBoolean(recursive)));
      }
    }
  }

  private static OrderRootType getJarDirectoryRootType(@Nullable String type) {
    for (PersistentOrderRootType rootType : OrderRootType.getAllPersistentTypes()) {
      if (rootType.name().equals(type)) {
        return rootType;
      }
    }
    return DEFAULT_JAR_DIRECTORY_TYPE;
  }

  @Override
  public void writeExternal(Element element) {
    final List<OrderRootType> rootTypes = LibraryImpl.sortRootTypes(getRootTypes());
    for (OrderRootType rootType : rootTypes) {
      final List<String> urls = new ArrayList<>(getDirectories(rootType));
      Collections.sort(urls, String.CASE_INSENSITIVE_ORDER);
      for (String url : urls) {
        final Element jarDirElement = new Element(JAR_DIRECTORY_ELEMENT);
        jarDirElement.setAttribute(URL_ATTR, url);
        jarDirElement.setAttribute(RECURSIVE_ATTR, Boolean.toString(isRecursive(rootType, url)));
        if (!rootType.equals(DEFAULT_JAR_DIRECTORY_TYPE)) {
          jarDirElement.setAttribute(ROOT_TYPE_ATTR, rootType.name());
        }
        element.addContent(jarDirElement);
      }
    }
  }

}
