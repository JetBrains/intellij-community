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
import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.XmlConfigurationMerger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public class StorageData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.StorageData");
  @NonNls public static final String COMPONENT = "component";
  @NonNls public static final String NAME = "name";

  final Map<String, Element> myComponentStates;
  protected final String myRootElementName;
  private int myHash = -1;

  public StorageData(final String rootElementName) {
    myComponentStates = new THashMap<String, Element>();
    myRootElementName = rootElementName;
  }

  StorageData(StorageData storageData) {
    myRootElementName = storageData.myRootElementName;
    myComponentStates = new THashMap<String, Element>(storageData.myComponentStates);
  }

  public void load(@NotNull Element rootElement) throws IOException {
    for (Iterator<Element> iterator = rootElement.getChildren(COMPONENT).iterator(); iterator.hasNext(); ) {
      Element element = iterator.next();
      String name = element.getAttributeValue(NAME);
      if (name == null) {
        LOG.info("Broken content in file : " + this);
        continue;
      }

      iterator.remove();

      if (element.getAttributes().size() > 1 || !element.getChildren().isEmpty()) {
        assert element.getAttributeValue(NAME) != null : "No name attribute for component: " + name + " in " + this;

        Element existingElement = myComponentStates.get(name);
        if (existingElement != null) {
          element = mergeElements(name, element, existingElement);
        }

        myComponentStates.put(name, element);
      }
    }
  }

  private static Element mergeElements(final String name, final Element element1, final Element element2) {
    ExtensionPoint<XmlConfigurationMerger> point = Extensions.getRootArea().getExtensionPoint("com.intellij.componentConfigurationMerger");
    for (XmlConfigurationMerger merger : point.getExtensions()) {
      if (merger.getComponentName().equals(name)) {
        return merger.merge(element1, element2);
      }
    }
    return element1;
  }

  @NotNull
  protected Element save() {
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

  public boolean hasState(final String componentName) {
      return myComponentStates.containsKey(componentName);
  }

  public void checkUnknownMacros(TrackingPathMacroSubstitutor pathMacroSubstitutor) {
    if (pathMacroSubstitutor == null) {
      return;
    }

    for (String componentName : myComponentStates.keySet()) {
      final Set<String> unknownMacros = PathMacrosCollector.getMacroNames(myComponentStates.get(componentName));
      if (!unknownMacros.isEmpty()) {
        pathMacroSubstitutor.addUnknownMacros(componentName, unknownMacros);
      }
    }
  }
}
