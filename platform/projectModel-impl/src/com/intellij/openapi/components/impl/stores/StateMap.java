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
package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.StringInterner;
import gnu.trove.THashMap;
import org.iq80.snappy.SnappyInputStream;
import org.iq80.snappy.SnappyOutputStream;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;

@SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
public final class StateMap implements StorageDataBase {
  private static final Logger LOG = Logger.getInstance(StateMap.class);

  public static final String COMPONENT = "component";
  public static final String NAME = "name";
  public static final String DEFAULT_EXT = ".xml";

  private static final Format XML_FORMAT = Format.getRawFormat().
    setTextMode(Format.TextMode.TRIM).
    setOmitEncoding(true).
    setOmitDeclaration(true);

  private final String[] names;
  private final AtomicReferenceArray<Object> states;

  public StateMap() {
    this(new String[]{}, new AtomicReferenceArray<Object>(0));
  }

  @NotNull
  public static StateMap fromMap(@NotNull Map<String, ?> map) {
    String[] names = ArrayUtil.toStringArray(map.keySet());
    Arrays.sort(names);
    AtomicReferenceArray<Object> states = new AtomicReferenceArray<Object>(names.length);
    for (int i = 0, n = names.length; i < n; i++) {
      states.set(i, map.get(names[i]));
    }
    return new StateMap(names, states);
  }

  @NotNull
  public Map<String, Object> toMap() {
    THashMap<String, Object> map = new THashMap<String, Object>(names.length);
    for (int i = 0; i < names.length; i++) {
      map.put(names[i], states.get(i));
    }
    return map;
  }

  private StateMap(@NotNull String[] names, @NotNull AtomicReferenceArray<Object> states) {
    this.names = names;
    this.states = states;
  }

  @NotNull
  /**
   * Sorted by name.
   */
  public String[] keys() {
    return names;
  }

  @Nullable
  public Object get(@NotNull String key) {
    int index = Arrays.binarySearch(names, key);
    return index < 0 ? null : states.get(index);
  }

  @NotNull
  public Element getElement(@NotNull String key, @NotNull Map<String, Element> newLiveStates) throws IOException {
    return stateToElement(key, get(key), newLiveStates);
  }

  @NotNull
  public static Element stateToElement(@NotNull String key, @Nullable Object state, @NotNull Map<String, Element> newLiveStates) throws IOException {
    if (state instanceof Element) {
      return ((Element)state).clone();
    }
    else {
      Element element = newLiveStates.get(key);
      if (element == null) {
        assert state != null;
        try {
          element = unarchiveState((byte[])state);
        }
        catch (JDOMException e) {
          throw new IOException(e);
        }
      }
      return element;
    }
  }

  public boolean isEmpty() {
    return names.length == 0;
  }

  @Nullable
  public Element getState(@NotNull String key) {
    Object state = get(key);
    return state instanceof Element ? (Element)state : null;
  }

  @Override
  public boolean hasState(@NotNull String key) {
    return get(key) instanceof Element;
  }

  public boolean hasStates() {
    if (isEmpty()) {
      return false;
    }

    for (int i = 0; i < names.length; i++) {
      if (states.get(i) instanceof Element) {
        return true;
      }
    }
    return false;
  }

  public void compare(@NotNull String key, @NotNull StateMap newStates, @NotNull Set<String> diffs) {
    Object oldState = get(key);
    Object newState = newStates.get(key);
    if (oldState instanceof Element) {
      if (!JDOMUtil.areElementsEqual((Element)oldState, (Element)newState)) {
        diffs.add(key);
      }
    }
    else {
      assert newState != null;
      assert oldState != null;
      if (getNewByteIfDiffers(key, newState, (byte[])oldState) != null) {
        diffs.add(key);
      }
    }
  }

  @Nullable
  public static byte[] getNewByteIfDiffers(@NotNull String key, @NotNull Object newState, @NotNull byte[] oldState) {
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
        LOG.debug(key + " " + StringUtil.repeat("=", 80 - key.length()) + "\nBefore:\n" + before + "\nAfter:\n" + after);
      }
    }
    return newBytes;
  }

  @NotNull
  private static byte[] archiveState(@NotNull Element state) {
    BufferExposingByteArrayOutputStream byteOut = new BufferExposingByteArrayOutputStream();
    try {
      OutputStreamWriter writer = new OutputStreamWriter(new SnappyOutputStream(byteOut), CharsetToolkit.UTF8_CHARSET);
      try {
        XMLOutputter xmlOutputter = new JDOMUtil.MyXMLOutputter();
        xmlOutputter.setFormat(XML_FORMAT);
        xmlOutputter.output(state, writer);
      }
      finally {
        writer.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return ArrayUtil.realloc(byteOut.getInternalBuffer(), byteOut.size());
  }

  @Nullable
  public Element getStateAndArchive(@NotNull String key) {
    int index = Arrays.binarySearch(names, key);
    if (index < 0) {
      return null;
    }

    Object state = states.get(index);
    if (!(state instanceof Element)) {
      return null;
    }

    if (states.compareAndSet(index, state, archiveState((Element)state))) {
      return (Element)state;
    }
    else {
      return getStateAndArchive(key);
    }
  }

  @NotNull
  private static Element unarchiveState(@NotNull byte[] state) throws IOException, JDOMException {
    return JDOMUtil.load(new SnappyInputStream(new ByteArrayInputStream(state)));
  }

  @NotNull
  public static String stateToString(@NotNull Object state) {
    Element element;
    if (state instanceof Element) {
      element = (Element)state;
    }
    else {
      try {
        element = unarchiveState((byte[])state);
      }
      catch (Throwable e) {
        LOG.error(e);
        return "internal error";
      }
    }
    return JDOMUtil.writeParent(element, "\n");
  }

  @Nullable
  static String getComponentNameIfValid(@NotNull Element element) {
    String name = element.getAttributeValue(NAME);
    if (StringUtil.isEmpty(name)) {
      LOG.warn("No name attribute for component in " + JDOMUtil.writeElement(element));
      return null;
    }
    return name;
  }

  @NotNull
  public static StateMap load(@NotNull Element rootElement, @Nullable PathMacroSubstitutor pathMacroSubstitutor, boolean intern) {
    if (pathMacroSubstitutor != null) {
      pathMacroSubstitutor.expandPaths(rootElement);
    }

    StringInterner interner = intern ? new StringInterner() : null;
    List<Element> children = rootElement.getChildren(COMPONENT);
    TreeMap<String, Element> map = new TreeMap<String, Element>();
    for (Element element : children) {
      String name = getComponentNameIfValid(element);
      if (name == null || !(element.getAttributes().size() > 1 || !element.getChildren().isEmpty())) {
        continue;
      }

      if (interner != null) {
        JDOMUtil.internElement(element, interner);
      }

      map.put(name, element);

      if (pathMacroSubstitutor instanceof TrackingPathMacroSubstitutor) {
        ((TrackingPathMacroSubstitutor)pathMacroSubstitutor).addUnknownMacros(name, PathMacrosCollector.getMacroNames(element));
      }

      // remove only after "getMacroNames" - some PathMacroFilter requires element name attribute
      element.removeAttribute(NAME);
    }
    return fromMap(map);
  }
}