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

import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
public final class StateMap {
  private static final Logger LOG = Logger.getInstance(StateMap.class);

  private static final Format XML_FORMAT = Format.getRawFormat().
    setTextMode(Format.TextMode.TRIM).
    setOmitEncoding(true).
    setOmitDeclaration(true);

  private final THashMap<String, Object> states;

  public StateMap() {
    states = new THashMap<String, Object>();
  }

  public StateMap(StateMap stateMap) {
    states = new THashMap<String, Object>((Map<String, Object>)stateMap.states);
  }

  @NotNull
  public Set<String> keys() {
    return states.keySet();
  }

  @NotNull
  public Collection<Object> values() {
    return states.values();
  }

  @Nullable
  public Object get(@NotNull String key) {
    return states.get(key);
  }

  @NotNull
  public Element getElement(@NotNull String key, @NotNull Map<String, Element> newLiveStates) {
    Object state = states.get(key);
    return stateToElement(key, state, newLiveStates);
  }

  @NotNull
  static Element stateToElement(@NotNull String key, @Nullable Object state, @NotNull Map<String, Element> newLiveStates) {
    if (state instanceof Element) {
      return ((Element)state).clone();
    }
    else {
      Element element = newLiveStates.get(key);
      if (element == null) {
        assert state != null;
        element = unarchiveState((byte[])state);
      }
      return element;
    }
  }

  public void put(@NotNull String key, @NotNull Object value) {
    states.put(key, value);
  }

  public boolean isEmpty() {
    return states.isEmpty();
  }

  @Nullable
  public Element getState(@NotNull String key) {
    Object state = states.get(key);
    return state instanceof Element ? (Element)state : null;
  }

  public boolean hasState(@NotNull String key) {
    return states.get(key) instanceof Element;
  }

  public boolean hasStates() {
    if (states.isEmpty()) {
      return false;
    }

    for (Object value : states.values()) {
      if (value instanceof Element) {
        return true;
      }
    }
    return false;
  }

  public void compare(@NotNull String key, @NotNull StateMap newStates, @NotNull Set<String> diffs) {
    Object oldState = states.get(key);
    Object newState = newStates.get(key);
    if (oldState instanceof Element) {
      if (!JDOMUtil.areElementsEqual((Element)oldState, (Element)newState)) {
        diffs.add(key);
      }
    }
    else {
      assert newState != null;
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
    Object state = states.get(key);
    if (!(state instanceof Element)) {
      return null;
    }

    states.put(key, archiveState((Element)state));
    return (Element)state;
  }

  @NotNull
  public static Element unarchiveState(@NotNull byte[] state) {
    try {
      return JDOMUtil.load(new SnappyInputStream(new ByteArrayInputStream(state)));
    }
    catch (IOException e) {
      throw new StateStorageException(e);
    }
    catch (JDOMException e) {
      throw new StateStorageException(e);
    }
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
  public Object remove(@NotNull String key) {
    return states.remove(key);
  }

  public int size() {
    return states.size();
  }

  public void forEachEntry(@NotNull TObjectObjectProcedure<String, Object> consumer) {
    states.forEachEntry(consumer);
  }
}