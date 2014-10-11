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
import com.intellij.openapi.components.StateSplitter;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DirectoryStorageData {
  private static final Logger LOG = Logger.getInstance(DirectoryStorageData.class);

  private Map<String, THashMap<File, Element>> myStates = new THashMap<String, THashMap<File, Element>>();
  private long myLastTimestamp = 0;
  private DirectoryStorageData myOriginalData;

  @NotNull
  public Set<String> getComponentNames() {
    return myStates.keySet();
  }

  static boolean isStorageFile(@NotNull VirtualFile file) {
    // ignore system files like .DS_Store on Mac
    return StringUtilRt.endsWithIgnoreCase(file.getNameSequence(), ".xml");
  }

  public void loadFrom(@Nullable VirtualFile dir, @Nullable TrackingPathMacroSubstitutor pathMacroSubstitutor) {
    if (dir == null || !dir.exists()) {
      return;
    }

    for (VirtualFile file : dir.getChildren()) {
      if (!isStorageFile(file)) {
        continue;
      }

      try {
        final Element element = JDOMUtil.loadDocument(file.contentsToByteArray()).detachRootElement();
        if (!element.getName().equals(StorageData.COMPONENT)) {
          LOG.error("Incorrect root tag name (" + element.getName() + ") in " + file.getPresentableUrl());
          continue;
        }

        String componentName = element.getAttributeValue(StorageData.NAME);
        if (componentName == null) {
          LOG.error("Component name isn't specified in " + file.getPresentableUrl());
          continue;
        }

        if (pathMacroSubstitutor != null) {
          pathMacroSubstitutor.expandPaths(element);
          pathMacroSubstitutor.addUnknownMacros(componentName, PathMacrosCollector.getMacroNames(element));
        }

        put(componentName, new File(file.getPath()), element, true);
      }
      catch (IOException e) {
        LOG.info("Unable to load state", e);
      }
      catch (JDOMException e) {
        LOG.info("Unable to load state", e);
      }
    }
  }

  public void put(@NotNull String componentName, @NotNull File file, @NotNull Element element, boolean updateTimestamp) {
    THashMap<File, Element> stateMap = myStates.get(componentName);
    if (stateMap == null) {
      stateMap = new THashMap<File, Element>();
      myStates.put(componentName, stateMap);
    }

    stateMap.put(file, element);
    if (updateTimestamp) {
      updateLastTimestamp(file);
    }
  }

  public void updateLastTimestamp(@NotNull File file) {
    myLastTimestamp = Math.max(myLastTimestamp, file.lastModified());
    if (myOriginalData != null) {
      myOriginalData.myLastTimestamp = myLastTimestamp;
    }
  }

  public long getLastTimeStamp() {
    return myLastTimestamp;
  }

  void processComponent(@NotNull String componentName, @NotNull TObjectObjectProcedure<File, Element> consumer) {
    THashMap<File, Element> map = myStates.get(componentName);
    if (map != null) {
      map.forEachEntry(consumer);
    }
  }

  @Override
  protected DirectoryStorageData clone() {
    final DirectoryStorageData result = new DirectoryStorageData();
    result.myStates = new THashMap<String, THashMap<File, Element>>(myStates);
    result.myLastTimestamp = myLastTimestamp;
    result.myOriginalData = this;
    return result;
  }

  public void clear() {
    myStates.clear();
    myOriginalData = null;
  }

  public boolean containsComponent(final String componentName) {
    return myStates.get(componentName) != null;
  }

  public void removeComponent(final String componentName) {
    myStates.remove(componentName);
  }

  @Nullable
  public <T> T getMergedState(String componentName, Class<T> stateClass, StateSplitter splitter, @Nullable T mergeInto) {
    final List<Element> subElements = new ArrayList<Element>();
    processComponent(componentName, new TObjectObjectProcedure<File, Element>() {
      @Override
      public boolean execute(File file, Element element) {
        final List children = element.getChildren();
        assert children.size() == 1 : JDOMUtil.writeElement(element, File.separator);
        Element subElement = (Element)children.get(0);
        subElement.detach();
        subElements.add(subElement);
        return true;
      }
    });

    final Element state = new Element(StorageData.COMPONENT);
    splitter.mergeStatesInto(state, subElements.toArray(new Element[subElements.size()]));
    removeComponent(componentName);

    return DefaultStateSerializer.deserializeState(state, stateClass, mergeInto);
  }
}
