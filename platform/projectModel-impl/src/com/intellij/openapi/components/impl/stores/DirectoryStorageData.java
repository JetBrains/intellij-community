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
import com.intellij.openapi.components.StateSplitter;
import com.intellij.openapi.components.StateSplitterEx;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.StringInterner;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.components.impl.stores.StateMap.getNewByteIfDiffers;

public class DirectoryStorageData implements StorageDataBase {
  private static final Logger LOG = Logger.getInstance(DirectoryStorageData.class);

  final Map<String, StateMap> states;

  public DirectoryStorageData() {
    this.states = new THashMap<String, StateMap>();
  }

  private DirectoryStorageData(@NotNull Map<String, StateMap> states) {
    this.states = states;
  }

  @NotNull
  public Map<String, Map<String, Object>> toMap() {
    THashMap<String, Map<String, Object>> map = new THashMap<String, Map<String, Object>>(states.size());
    for (Map.Entry<String, StateMap> entry : states.entrySet()) {
      map.put(entry.getKey(), entry.getValue().toMap());
    }
    return map;
  }

  @NotNull
  public static DirectoryStorageData fromMap(@NotNull Map<String, Map<String, Object>> map) {
    Map<String, StateMap> states = new THashMap<String, StateMap>(map.size());
    for (Map.Entry<String, Map<String, Object>> entry : map.entrySet()) {
      states.put(entry.getKey(), StateMap.fromMap(entry.getValue()));
    }
    return new DirectoryStorageData(states);
  }

  @NotNull
  public Set<String> getComponentNames() {
    return states.keySet();
  }

  boolean isEmpty() {
    return states.isEmpty();
  }

  @NotNull
  public static DirectoryStorageData loadFrom(@Nullable VirtualFile dir, @Nullable TrackingPathMacroSubstitutor pathMacroSubstitutor) {
    if (dir == null || !dir.exists()) {
      return new DirectoryStorageData();
    }

    StringInterner interner = new StringInterner();
    Map<String, Map<String, Object>> map = new THashMap<String, Map<String, Object>>();
    for (VirtualFile file : dir.getChildren()) {
      // ignore system files like .DS_Store on Mac
      if (!StringUtilRt.endsWithIgnoreCase(file.getNameSequence(), StateMap.DEFAULT_EXT)) {
        continue;
      }

      try {
        if (file.getLength() == 0) {
          LOG.warn("Ignore empty file " + file.getPath());
          continue;
        }

        Element element = JDOMUtil.load(file.getInputStream());
        String name = StateMap.getComponentNameIfValid(element);
        if (name == null) {
          continue;
        }

        if (!element.getName().equals(StateMap.COMPONENT)) {
          LOG.error("Incorrect root tag name (" + element.getName() + ") in " + file.getPresentableUrl());
          continue;
        }

        List<Element> elementChildren = element.getChildren();
        if (elementChildren.isEmpty()) {
          continue;
        }

        Element state = (Element)elementChildren.get(0).detach();
        JDOMUtil.internElement(state, interner);
        if (pathMacroSubstitutor != null) {
          pathMacroSubstitutor.expandPaths(state);
          pathMacroSubstitutor.addUnknownMacros(name, PathMacrosCollector.getMacroNames(state));
        }
        setState(map, name, file.getName(), state);
      }
      catch (Throwable e) {
        LOG.warn("Unable to load state", e);
      }
    }
    return fromMap(map);
  }

  @Nullable
  public static Map<String, Map<String, Object>> setStateAndCloneIfNeed(@NotNull String componentName,
                                                                        @Nullable String fileName,
                                                                        @Nullable Element newState,
                                                                        @NotNull DirectoryStorageData storageData) {
    StateMap fileToState = storageData.states.get(componentName);
    Object oldState = fileToState == null || fileName == null ? null : fileToState.get(fileName);
    if (fileName == null || newState == null || JDOMUtil.isEmpty(newState)) {
      if (fileName == null) {
        if (fileToState == null) {
          return null;
        }
      }
      else if (oldState == null) {
        return null;
      }

      Map<String, Map<String, Object>> newStorageData = storageData.toMap();
      if (fileName == null) {
        newStorageData.remove(componentName);
      }
      else {
        Map<String, Object> clonedFileToState = newStorageData.get(componentName);
        if (clonedFileToState.size() == 1) {
          newStorageData.remove(componentName);
        }
        else {
          clonedFileToState.remove(fileName);
          if (clonedFileToState.isEmpty()) {
            newStorageData.remove(componentName);
          }
        }
      }
      return newStorageData;
    }

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

    Map<String, Map<String, Object>> newStorageData = storageData.toMap();
    put(newStorageData, componentName, fileName, newBytes == null ? newState : newBytes);
    return newStorageData;
  }

  @Nullable
  public static Object setState(@NotNull Map<String, Map<String, Object>> states, @NotNull String componentName, @Nullable String fileName, @Nullable Element newState) {
    Map<String, Object> fileToState = states.get(componentName);
    if (fileName == null || newState == null || JDOMUtil.isEmpty(newState)) {
      if (fileToState == null) {
        return null;
      }
      else if (fileName == null) {
        return states.remove(componentName);
      }
      else {
        Object oldState = fileToState.remove(fileName);
        if (fileToState.isEmpty()) {
          states.remove(componentName);
        }
        return oldState;
      }
    }

    if (fileToState == null) {
      fileToState = new THashMap<String, Object>();
      fileToState.put(fileName, newState);
      states.put(componentName, fileToState);
    }
    else {
      Object oldState = fileToState.get(fileName);

      byte[] newBytes = null;
      if (oldState instanceof Element) {
        if (JDOMUtil.areElementsEqual((Element)oldState, newState)) {
          return null;
        }
      }
      else if (oldState != null) {
        newBytes = getNewByteIfDiffers(fileName, newState, (byte[])oldState);
        if (newBytes == null) {
          return null;
        }
      }

      fileToState.put(fileName, newBytes == null ? newState : newBytes);
    }
    return newState;
  }

  private static void put(@NotNull Map<String, Map<String, Object>> states, @NotNull String componentName, @NotNull String fileName, @NotNull Object state) {
    Map<String, Object> fileToState = states.get(componentName);
    if (fileToState == null) {
      fileToState = new THashMap<String, Object>();
      states.put(componentName, fileToState);
    }
    fileToState.put(fileName, state);
  }

  public void clear() {
    states.clear();
  }

  @Override
  public boolean hasState(@NotNull String componentName) {
    StateMap fileToState = states.get(componentName);
    return fileToState != null && fileToState.hasStates();
  }

  @Nullable
  public Element getCompositeStateAndArchive(@NotNull String componentName, @SuppressWarnings("deprecation") @NotNull StateSplitter splitter) {
    StateMap fileToState = states.get(componentName);
    Element state = new Element(StateMap.COMPONENT);
    if (fileToState == null || fileToState.isEmpty()) {
      return state;
    }

    if (splitter instanceof StateSplitterEx) {
      for (String fileName : fileToState.keys()) {
        Element subState = fileToState.getStateAndArchive(fileName);
        if (subState == null) {
          return null;
        }
        ((StateSplitterEx)splitter).mergeStateInto(state, subState);
      }
    }
    else {
      List<Element> subElements = new SmartList<Element>();
      for (String fileName : fileToState.keys()) {
        Element subState = fileToState.getStateAndArchive(fileName);
        if (subState == null) {
          return null;
        }
        subElements.add(subState);
      }

      if (!subElements.isEmpty()) {
        splitter.mergeStatesInto(state, subElements.toArray(new Element[subElements.size()]));
      }
    }
    return state;
  }

  @NotNull
  String[] getFileNames(@NotNull String componentName) {
    StateMap fileToState = states.get(componentName);
    return fileToState == null || fileToState.isEmpty() ? ArrayUtil.EMPTY_STRING_ARRAY : fileToState.keys();
  }
}
