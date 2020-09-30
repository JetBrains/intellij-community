// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.CompositePathMacroFilter;
import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jdom.Element;
import org.jdom.Parent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class FileStorageCoreUtil {
  private static final Logger LOG = Logger.getInstance(FileStorageCoreUtil.class);

  public static final String COMPONENT = "component";
  public static final String NAME = "name";
  public static final String DEFAULT_EXT = PathManager.DEFAULT_EXT;

  public static @NotNull Map<String, Element> load(@NotNull Element rootElement, @Nullable PathMacroSubstitutor pathMacroSubstitutor, boolean internElements) {
    if (pathMacroSubstitutor != null) {
      pathMacroSubstitutor.expandPaths(rootElement);
    }

    List<Element> children = rootElement.getChildren(COMPONENT);
    if (children.isEmpty() && rootElement.getName().equals(COMPONENT) && rootElement.getAttributeValue(NAME) != null) {
      // exclusive component data
      // singleton must be not used here - later we modify list
      children = new SmartList<>(rootElement);
    }

    CompositePathMacroFilter filter = null;

    Map<String, Element> map = new TreeMap<>();
    for (Iterator<Element> iterator = children.iterator(); iterator.hasNext(); ) {
      Element element = iterator.next();
      String name = getComponentNameIfValid(element);
      if (name == null || !(element.getAttributes().size() > 1 || !element.getChildren().isEmpty())) {
        continue;
      }

      // so, PathMacroFilter can easily find component name (null parent)
      iterator.remove();

      if (pathMacroSubstitutor instanceof TrackingPathMacroSubstitutor) {
        if (filter == null) {
          filter = new CompositePathMacroFilter(PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME.getExtensionList());
        }

        ((TrackingPathMacroSubstitutor)pathMacroSubstitutor)
          .addUnknownMacros(name, PathMacrosCollector.getMacroNames(element, filter, PathMacrosImpl.getInstanceEx()));
      }

      // remove only after "getMacroNames" - some PathMacroFilter requires element name attribute
      element.removeAttribute(NAME);

      map.put(name, internElements ? JDOMUtil.internElement(element) : element);
    }
    return map;
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

  @Nullable
  public static String findComponentName(@NotNull Element element) {
    Element componentElement = element;
    while (true) {
      Parent parent = componentElement.getParent();
      if (!(parent instanceof Element)) {
        break;
      }

      componentElement = (Element)parent;
    }

    return StringUtil.nullize(componentElement.getAttributeValue(NAME));
  }
}
