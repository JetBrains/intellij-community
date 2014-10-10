/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.XmlConfigurationMerger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.SmartHashSet;
import gnu.trove.THashMap;
import org.iq80.snappy.SnappyInputStream;
import org.iq80.snappy.SnappyOutputStream;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

@SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
public class StorageData {
  private static final Logger LOG = Logger.getInstance(StorageData.class);
  @NonNls public static final String COMPONENT = "component";
  @NonNls public static final String NAME = "name";

  private final Map<String, Object> myComponentStates;

  protected final String myRootElementName;

  public StorageData(@NotNull String rootElementName) {
    myComponentStates = new THashMap<String, Object>();
    myRootElementName = rootElementName;
  }

  StorageData(@NotNull StorageData storageData) {
    myRootElementName = storageData.myRootElementName;
    myComponentStates = new THashMap<String, Object>(storageData.myComponentStates);
  }

  @NotNull
  public Set<String> getComponentNames() {
    return myComponentStates.keySet();
  }

  public void load(@NotNull Element rootElement, @Nullable PathMacroSubstitutor pathMacroSubstitutor, boolean intern) {
    if (pathMacroSubstitutor != null) {
      pathMacroSubstitutor.expandPaths(rootElement);
    }

    for (Iterator<Element> iterator = rootElement.getChildren(COMPONENT).iterator(); iterator.hasNext(); ) {
      Element element = iterator.next();
      String name = element.getAttributeValue(NAME);
      if (StringUtil.isEmpty(name)) {
        LOG.warn("No name attribute for component in " + this);
        continue;
      }

      if (element.getAttributes().size() > 1 || !element.getChildren().isEmpty()) {
        iterator.remove();
        if (intern) {
          IdeaPluginDescriptorImpl.internJDOMElement(element);
        }

        Object serverElement = myComponentStates.get(name);
        if (serverElement != null) {
          element = mergeElements(name, element, (Element)serverElement);
        }

        myComponentStates.put(name, element);

        if (pathMacroSubstitutor instanceof TrackingPathMacroSubstitutor) {
          ((TrackingPathMacroSubstitutor)pathMacroSubstitutor).addUnknownMacros(name, PathMacrosCollector.getMacroNames(element));
        }
      }

      // remove only after "getMacroNames" - some PathMacroFilter requires element name attribute
      element.removeAttribute(NAME);
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

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  protected Element save(@NotNull Map<String, Element> newLiveStates) {
    if (myComponentStates.isEmpty()) {
      return null;
    }

    Element rootElement = new Element(myRootElementName);
    String[] componentNames = ArrayUtil.toStringArray(myComponentStates.keySet());
    Arrays.sort(componentNames);
    for (String componentName : componentNames) {
      assert componentName != null;
      Element element;
      Object state = myComponentStates.get(componentName);
      if (state instanceof Element) {
        element = ((Element)state).clone();
      }
      else {
        element = newLiveStates.get(componentName);
        if (element == null) {
          try {
            element = unarchiveState((byte[])state);
          }
          catch (IOException e) {
            throw new StateStorageException(e);
          }
          catch (JDOMException e) {
            throw new StateStorageException(e);
          }
        }
      }

      // name attribute should be first
      assert element != null;
      List<Attribute> elementAttributes = element.getAttributes();
      if (elementAttributes.isEmpty()) {
        element.setAttribute(NAME, componentName);
      }
      else {
        Attribute nameAttribute = element.getAttribute(NAME);
        if (nameAttribute == null) {
          nameAttribute = new Attribute(NAME, componentName);
          elementAttributes.add(0, nameAttribute);
        }
        else {
          nameAttribute.setValue(componentName);
          if (elementAttributes.get(0) != nameAttribute) {
            elementAttributes.remove(nameAttribute);
            elementAttributes.add(0, nameAttribute);
          }
        }
      }

      rootElement.addContent(element);
    }
    return rootElement;
  }

  @Nullable
  public Element getState(@NotNull String name) {
    Object state = myComponentStates.get(name);
    return state instanceof Element ? (Element)state : null;
  }

  @Nullable
  public Element getStateAndArchive(@NotNull String name) {
    Object state = myComponentStates.get(name);
    if (!(state instanceof Element)) {
      return null;
    }

    myComponentStates.put(name, archiveState((Element)state));
    return (Element)state;
  }

  @NotNull
  private static byte[] archiveState(@NotNull Element state) {
    BufferExposingByteArrayOutputStream byteOut = new BufferExposingByteArrayOutputStream();
    OutputStream out = null;
    try {
      try {
        out = new SnappyOutputStream(byteOut);
        JDOMUtil.writeParent(state, out, "\n");
      }
      finally {
        if (out != null) {
          out.close();
        }
      }
    }
    catch (IOException e) {
      throw new StateStorageException(e);
    }
    return ArrayUtil.realloc(byteOut.getInternalBuffer(), byteOut.size());
  }

  @NotNull
  private static Element unarchiveState(@NotNull byte[] state) throws IOException, JDOMException {
    InputStream in = null;
    try {
      in = new SnappyInputStream(new ByteArrayInputStream(state));
      //noinspection ConstantConditions
      return JDOMUtil.loadDocument(in).detachRootElement();
    }
    finally {
      if (in != null) {
        in.close();
      }
    }
  }

  @Nullable
  static StorageData setStateAndCloneIfNeed(@NotNull String componentName, @Nullable Element newState, @NotNull StorageData storageData, @NotNull Map<String, Element> newLiveStates) {
    Object oldState = storageData.myComponentStates.get(componentName);
    if (newState == null || JDOMUtil.isEmpty(newState)) {
      if (oldState == null) {
        return null;
      }

      StorageData newStorageData = storageData.clone();
      newStorageData.myComponentStates.remove(componentName);
      return newStorageData;
    }

    prepareElement(newState);

    newLiveStates.put(componentName, newState);

    byte[] newBytes = null;
    if (oldState instanceof Element) {
      if (JDOMUtil.areElementsEqual((Element)oldState, newState)) {
        return null;
      }
    }
    else if (oldState != null) {
      newBytes = getNewByteIfDiffers(componentName, newState, (byte[])oldState);
      if (newBytes == null) {
        return null;
      }
    }

    StorageData newStorageData = storageData.clone();
    newStorageData.myComponentStates.put(componentName, newBytes == null ? newState : newBytes);
    return newStorageData;
  }

  @Nullable
  final Object setState(@NotNull String componentName, @Nullable Element newState, @NotNull Map<String, Element> newLiveStates) {
    if (newState == null || JDOMUtil.isEmpty(newState)) {
      return myComponentStates.remove(componentName);
    }

    prepareElement(newState);

    newLiveStates.put(componentName, newState);

    Object oldState = myComponentStates.get(componentName);

    byte[] newBytes = null;
    if (oldState instanceof Element) {
      if (JDOMUtil.areElementsEqual((Element)oldState, newState)) {
        return null;
      }
    }
    else if (oldState != null) {
      newBytes = getNewByteIfDiffers(componentName, newState, (byte[])oldState);
      if (newBytes == null) {
        return null;
      }
    }

    myComponentStates.put(componentName, newBytes == null ? newState : newBytes);
    return newState;
  }

  @Nullable
  private static byte[] getNewByteIfDiffers(@NotNull String componentName, @NotNull Object newState, @NotNull byte[] oldState) {
    byte[] newBytes = newState instanceof Element ? archiveState((Element)newState) : (byte[])newState;
    if (Arrays.equals(newBytes, oldState)) {
      return null;
    }
    else if (LOG.isDebugEnabled() && SystemProperties.getBooleanProperty("idea.log.changed.components", false)) {
      String before = stateToString(oldState);
      String after = stateToString(newState);
      if (before.equals(after)) {
        LOG.debug("Serialization error: serialized are different, but unserialized are equal");
      }
      else {
        LOG.debug(componentName + " " + StringUtil.repeat("=", 80 - componentName.length()) + "\nBefore:\n" + before + "\nAfter:\n" + after);
      }
    }
    return newBytes;
  }

  @NotNull
  private static String stateToString(@NotNull Object state) {
    Element element;
    if (state instanceof Element) {
      element = (Element)state;
    }
    else {
      try {
        element = unarchiveState((byte[])state);
      }
      catch (IOException e) {
        LOG.error(e);
        return "internal error";
      }
      catch (JDOMException e) {
        LOG.error(e);
        return "internal error";
      }
    }
    return JDOMUtil.writeParent(element, "\n");
  }

  private static void prepareElement(@NotNull Element element) {
    element.setName(COMPONENT);
  }

  @Override
  public StorageData clone() {
    return new StorageData(this);
  }

  // newStorageData - myComponentStates contains only live (unarchived) states
  public Set<String> getChangedComponentNames(@NotNull StorageData newStorageData, @Nullable PathMacroSubstitutor substitutor) {
    Set<String> bothStates = new SmartHashSet<String>(myComponentStates.keySet());
    bothStates.retainAll(newStorageData.myComponentStates.keySet());

    Set<String> diffs = new SmartHashSet<String>();
    diffs.addAll(newStorageData.myComponentStates.keySet());
    diffs.addAll(myComponentStates.keySet());
    diffs.removeAll(bothStates);

    for (String componentName : bothStates) {
      Object oldState = myComponentStates.get(componentName);
      Object newState = newStorageData.myComponentStates.get(componentName);
      if (oldState instanceof Element) {
        if (!JDOMUtil.areElementsEqual((Element)oldState, (Element)newState)) {
          diffs.add(componentName);
        }
      }
      else if (getNewByteIfDiffers(componentName, newState, (byte[])oldState) != null) {
        diffs.add(componentName);
      }
    }
    return diffs;
  }

  public boolean hasState(@NotNull String componentName) {
    return myComponentStates.get(componentName) instanceof Element;
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
