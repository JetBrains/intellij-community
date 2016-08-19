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
import com.intellij.openapi.components.StateSplitterEx;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.StringInterner;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DirectoryStorageUtil {
  private static final Logger LOG = Logger.getInstance(DirectoryStorageUtil.class);

  @NotNull
  public static Map<String, Element> loadFrom(@Nullable VirtualFile dir, @Nullable TrackingPathMacroSubstitutor pathMacroSubstitutor) {
    if (dir == null || !dir.exists()) {
      return Collections.emptyMap();
    }

    StringInterner interner = new StringInterner();
    Map<String, Element> fileToState = new THashMap<>();
    for (VirtualFile file : dir.getChildren()) {
      // ignore system files like .DS_Store on Mac
      if (!StringUtilRt.endsWithIgnoreCase(file.getNameSequence(), FileStorageCoreUtil.DEFAULT_EXT)) {
        continue;
      }

      try {
        if (file.getLength() == 0) {
          LOG.warn("Ignore empty file " + file.getPath());
          continue;
        }

        Element element = JDOMUtil.load(file.getInputStream());
        String componentName = FileStorageCoreUtil.getComponentNameIfValid(element);
        if (componentName == null) {
          continue;
        }

        if (!element.getName().equals(FileStorageCoreUtil.COMPONENT)) {
          LOG.error("Incorrect root tag name (" + element.getName() + ") in " + file.getPresentableUrl());
          continue;
        }

        List<Element> elementChildren = element.getChildren();
        if (elementChildren.isEmpty()) {
          continue;
        }

        Element state = (Element)elementChildren.get(0).detach();
        if (JDOMUtil.isEmpty(state)) {
          continue;
        }

        JDOMUtil.internElement(state, interner);
        if (pathMacroSubstitutor != null) {
          pathMacroSubstitutor.expandPaths(state);
          pathMacroSubstitutor.addUnknownMacros(componentName, PathMacrosCollector.getMacroNames(state));
        }

        fileToState.put(file.getName(), state);
      }
      catch (Throwable e) {
        LOG.warn("Unable to load state", e);
      }
    }
    return fileToState;
  }

  @Nullable
  public static Element getCompositeState(@NotNull Map<String, Element> fileToState, @NotNull StateSplitterEx splitter) {
    Element state = new Element(FileStorageCoreUtil.COMPONENT);
    if (fileToState.isEmpty()) {
      return state;
    }

    for (String fileName : fileToState.keySet()) {
      Element subState = fileToState.get(fileName);
      if (subState == null) {
        return null;
      }
      splitter.mergeStateInto(state, subState);
    }
    return state;
  }
}
