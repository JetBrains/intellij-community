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
package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.XmlConfigurationMerger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class StorageData {
  private static final Logger LOG = Logger.getInstance(StorageData.class);
  @NonNls public static final String COMPONENT = "component";
  @NonNls public static final String NAME = "name";

  final Map<String, Element> myComponentStates;
  protected final String myRootElementName;
  private int myHash = -1;

  public StorageData(@NotNull String rootElementName) {
    myComponentStates = new THashMap<String, Element>();
    myRootElementName = rootElementName;
  }

  StorageData(@NotNull StorageData storageData) {
    myRootElementName = storageData.myRootElementName;
    myComponentStates = new THashMap<String, Element>(storageData.myComponentStates);
  }

  public void load(@NotNull Element rootElement, @Nullable PathMacroSubstitutor pathMacroSubstitutor, boolean intern) {
    if (pathMacroSubstitutor != null) {
      pathMacroSubstitutor.expandPaths(rootElement);
    }

    for (Iterator<Element> iterator = rootElement.getChildren(COMPONENT).iterator(); iterator.hasNext(); ) {
      Element element = iterator.next();
      String name = element.getAttributeValue(NAME);
      if (name == null) {
        LOG.info("Broken content in file : " + this);
        continue;
      }

      if (element.getAttributes().size() > 1 || !element.getChildren().isEmpty()) {
        assert element.getAttributeValue(NAME) != null : "No name attribute for component: " + name + " in " + this;

        iterator.remove();
        if (intern) {
          IdeaPluginDescriptorImpl.internJDOMElement(element);
        }

        Element serverElement = myComponentStates.get(name);
        if (serverElement != null) {
          element = mergeElements(name, element, serverElement);
        }

        myComponentStates.put(name, element);
      }
    }

    if (pathMacroSubstitutor instanceof TrackingPathMacroSubstitutor) {
      for (String componentName : myComponentStates.keySet()) {
        ((TrackingPathMacroSubstitutor)pathMacroSubstitutor).addUnknownMacros(componentName, PathMacrosCollector.getMacroNames(myComponentStates.get(componentName)));
      }
    }
  }

  @NotNull
  private static Element mergeElements(@NotNull String name, @NotNull Element localElement, @NotNull Element serverElement) {
    ExtensionPoint<XmlConfigurationMerger> point = Extensions.getRootArea().getExtensionPoint("com.intellij.componentConfigurationMerger");
    for (XmlConfigurationMerger merger : point.getExtensions()) {
      if (merger.getComponentName().equals(name)) {
        return merger.merge(serverElement, localElement);
      }
    }
    return serverElement;
  }

  @Nullable
  protected Element save() {
    if (myComponentStates.isEmpty()) {
      return null;
    }

    Element rootElement = new Element(myRootElementName);
    String[] componentNames = ArrayUtil.toStringArray(myComponentStates.keySet());
    Arrays.sort(componentNames);
    for (String componentName : componentNames) {
      assert componentName != null;
      final Element element = myComponentStates.get(componentName);

      if (element.getAttribute(NAME) == null) element.setAttribute(NAME, componentName);

      rootElement.addContent(element.clone());
    }
    return rootElement;
  }

  @Nullable
  public Element getState(final String name) {
    final Element element = myComponentStates.get(name);
    if (element != null) {
      assert element.getAttributeValue(NAME) != null : "No name attribute for component: " + name + " in " + this;
      element.removeAttribute(NAME);
    }
    return element;
  }

  void removeState(final String componentName) {
    myComponentStates.remove(componentName);
    clearHash();
  }

  void setState(@NotNull final String componentName, final Element element) {
    element.setName(COMPONENT);

    //componentName should be first!
    final List<Attribute> attributes = new ArrayList<Attribute>(element.getAttributes());
    for (Attribute attribute : attributes) {
      element.removeAttribute(attribute);
    }

    element.setAttribute(NAME, componentName);

    for (Attribute attribute : attributes) {
      element.setAttribute(attribute.getName(), attribute.getValue());
    }

    myComponentStates.put(componentName, element);
    clearHash();
  }

  @Override
  public StorageData clone() {
    return new StorageData(this);
  }

  public final int getHash() {
    if (myHash == -1) {
      myHash = computeHash();
      if (myHash == -1) {
        myHash = 0;
      }
    }
    return myHash;
  }

  protected int computeHash() {
    int result = 0;
    for (String name : myComponentStates.keySet()) {
      result = 31 * result + name.hashCode();
      result = 31 * result + JDOMUtil.getTreeHash(myComponentStates.get(name));
    }
    return result;
  }

  protected void clearHash() {
    myHash = -1;
  }

  public Set<String> getDifference(final StorageData storageData, PathMacroSubstitutor substitutor) {
    Set<String> bothStates = new THashSet<String>(myComponentStates.keySet());
    bothStates.retainAll(storageData.myComponentStates.keySet());

    Set<String> diffs = new THashSet<String>();
    diffs.addAll(storageData.myComponentStates.keySet());
    diffs.addAll(myComponentStates.keySet());
    diffs.removeAll(bothStates);

    for (String componentName : bothStates) {
      final Element e1 = myComponentStates.get(componentName);
      final Element e2 = storageData.myComponentStates.get(componentName);

      // some configurations want to collapse path elements in writeExternal so make sure paths are expanded
      if (substitutor != null) {
        substitutor.expandPaths(e2);
      }

      if (!JDOMUtil.areElementsEqual(e1, e2)) {
        diffs.add(componentName);
      }
    }

    return diffs;
  }

  public boolean isEmpty() {
    return myComponentStates.isEmpty();
  }

  public boolean hasState(@NotNull String componentName) {
      return myComponentStates.containsKey(componentName);
  }

  @NotNull
  public static Element load(@NotNull VirtualFile file) throws IOException, JDOMException {
    InputStream stream = file.getInputStream();
    try {
      return JDOMUtil.loadDocument(stream).getRootElement();
    }
    finally {
      stream.close();
    }
  }
}
