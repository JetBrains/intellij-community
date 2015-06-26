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
import com.intellij.util.SmartList;
import com.intellij.util.containers.StringInterner;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.components.impl.stores.StateMap.getNewByteIfDiffers;

public class DirectoryStorageData extends StorageDataBase {
  private static final Logger LOG = Logger.getInstance(DirectoryStorageData.class);

  public static final String DEFAULT_EXT = ".xml";

  private final Map<String, StateMap> myStates;

  public DirectoryStorageData() {
    this(new THashMap<String, StateMap>());
  }

  private DirectoryStorageData(@NotNull Map<String, StateMap> states) {
    myStates = states;
  }

  @Override
  @NotNull
  public Set<String> getComponentNames() {
    return myStates.keySet();
  }

  boolean isEmpty() {
    return myStates.isEmpty();
  }

  public static boolean isStorageFile(@NotNull VirtualFile file) {
    // ignore system files like .DS_Store on Mac
    return StringUtilRt.endsWithIgnoreCase(file.getNameSequence(), DEFAULT_EXT);
  }

  public void loadFrom(@Nullable VirtualFile dir, @Nullable TrackingPathMacroSubstitutor pathMacroSubstitutor) {
    if (dir == null || !dir.exists()) {
      return;
    }

    StringInterner interner = new StringInterner();
    for (VirtualFile file : dir.getChildren()) {
      if (!isStorageFile(file)) {
        continue;
      }

      try {
        if (file.getLength() == 0) {
          LOG.warn("Ignore empty file " + file.getPath());
          continue;
        }

        Element element = JDOMUtil.load(file.getInputStream());
        String name = StorageData.getComponentNameIfValid(element);
        if (name == null) {
          continue;
        }

        if (!element.getName().equals(StorageData.COMPONENT)) {
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
        setState(name, file.getName(), state);
      }
      catch (Throwable e) {
        LOG.warn("Unable to load state", e);
      }
    }
  }

  @Nullable
  public static DirectoryStorageData setStateAndCloneIfNeed(@NotNull String componentName,
                                                            @Nullable String fileName,
                                                            @Nullable Element newState,
                                                            @NotNull DirectoryStorageData storageData) {
    StateMap fileToState = storageData.myStates.get(componentName);
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

      DirectoryStorageData newStorageData = storageData.clone();
      if (fileName == null) {
        newStorageData.myStates.remove(componentName);
      }
      else {
        StateMap clonedFileToState = newStorageData.myStates.get(componentName);
        if (clonedFileToState.size() == 1) {
          newStorageData.myStates.remove(componentName);
        }
        else {
          clonedFileToState.remove(fileName);
          if (clonedFileToState.isEmpty()) {
            newStorageData.myStates.remove(componentName);
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

    DirectoryStorageData newStorageData = storageData.clone();
    newStorageData.put(componentName, fileName, newBytes == null ? newState : newBytes);
    return newStorageData;
  }

  @Nullable
  public Object setState(@NotNull String componentName, @Nullable String fileName, @Nullable Element newState) {
    StateMap fileToState = myStates.get(componentName);
    if (fileName == null || newState == null || JDOMUtil.isEmpty(newState)) {
      if (fileToState == null) {
        return null;
      }
      else if (fileName == null) {
        return myStates.remove(componentName);
      }
      else {
        Object oldState = fileToState.remove(fileName);
        if (fileToState.isEmpty()) {
          myStates.remove(componentName);
        }
        return oldState;
      }
    }

    if (fileToState == null) {
      fileToState = new StateMap();
      fileToState.put(fileName, newState);
      myStates.put(componentName, fileToState);
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

  private void put(@NotNull String componentName, @NotNull String fileName, @NotNull Object state) {
    StateMap fileToState = myStates.get(componentName);
    if (fileToState == null) {
      fileToState = new StateMap();
      myStates.put(componentName, fileToState);
    }
    fileToState.put(fileName, state);
  }

  void processComponent(@NotNull String componentName, @NotNull TObjectObjectProcedure<String, Object> consumer) {
    StateMap map = myStates.get(componentName);
    if (map != null) {
      map.forEachEntry(consumer);
    }
  }

  @Override
  protected DirectoryStorageData clone() {
    return new DirectoryStorageData(new THashMap<String, StateMap>(myStates));
  }

  public void clear() {
    myStates.clear();
  }

  @Override
  public boolean hasState(@NotNull String componentName) {
    StateMap fileToState = myStates.get(componentName);
    return fileToState != null && fileToState.hasStates();
  }

  @Nullable
  public Element getCompositeStateAndArchive(@NotNull String componentName, @SuppressWarnings("deprecation") @NotNull StateSplitter splitter) {
    StateMap fileToState = myStates.get(componentName);
    Element state = new Element(StorageData.COMPONENT);
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
  public Element stateToElement(@NotNull String key, @Nullable Object state) {
    return StateMap.stateToElement(key, state, Collections.<String, Element>emptyMap());
  }

  @NotNull
  public Set<String> getFileNames(@NotNull String componentName) {
    StateMap fileToState = myStates.get(componentName);
    return fileToState == null || fileToState.isEmpty() ? Collections.<String>emptySet() : fileToState.keys();
  }
}
