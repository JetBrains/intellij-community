// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.StateSplitterEx;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.Strings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DirectoryStorageUtil {
  private static final Logger LOG = Logger.getInstance(DirectoryStorageUtil.class);

  public static @NotNull Map<String, Element> loadFrom(@NotNull Path dir, @Nullable PathMacroSubstitutor pathMacroSubstitutor)
    throws IOException {

    try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
      Map<String, Element> fileToState = new HashMap<>();
      for (Path file : files) {
        // ignore system files like .DS_Store on Mac
        if (!Strings.endsWithIgnoreCase(file.toString(), FileStorageCoreUtil.DEFAULT_EXT)) {
          continue;
        }

        try {
          Element element = JDOMUtil.load(file);
          String componentName = FileStorageCoreUtil.getComponentNameIfValid(element);
          if (componentName == null) {
            continue;
          }

          if (!element.getName().equals(FileStorageCoreUtil.COMPONENT)) {
            LOG.error("Incorrect root tag name (" + element.getName() + ") in " + file);
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

          fileToState.put(file.getFileName().toString(), state);
        }
        catch (Throwable e) {
          if (e.getMessage().startsWith("Unexpected End-of-input in prolog")) {
            LOG.warn("Ignore empty file " + file);
          }
          else {
            LOG.warn("Unable to load state from " + file, e);
          }
        }
      }
      return fileToState;
    }
    catch (NoSuchFileException | NotDirectoryException ignore) {
      return Collections.emptyMap();
    }
  }

  public static @Nullable Element getCompositeState(@NotNull Map<String, Element> fileToState, @NotNull StateSplitterEx splitter) {
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
