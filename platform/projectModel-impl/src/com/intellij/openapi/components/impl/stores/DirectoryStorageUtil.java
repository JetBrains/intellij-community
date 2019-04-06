// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.StateSplitterEx;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
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
  public static Map<String, Element> loadFrom(@Nullable VirtualFile dir, @Nullable PathMacroSubstitutor pathMacroSubstitutor) {
    if (dir == null || !dir.exists()) {
      return Collections.emptyMap();
    }

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

        Element state = elementChildren.get(0).detach();
        if (JDOMUtil.isEmpty(state)) {
          continue;
        }

        if (pathMacroSubstitutor != null) {
          pathMacroSubstitutor.expandPaths(state);
          if (pathMacroSubstitutor instanceof TrackingPathMacroSubstitutor) {
            ((TrackingPathMacroSubstitutor)pathMacroSubstitutor).addUnknownMacros(componentName, PathMacrosCollector.getMacroNames(state));
          }
        }

        Element newState = JDOMUtil.internElement(state);
        fileToState.put(file.getName(), newState);
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
