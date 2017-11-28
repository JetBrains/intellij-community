/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.components.CompositePathMacroFilter;
import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.StringInterner;
import org.jdom.Element;
import org.jdom.Parent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

public class FileStorageCoreUtil {
  private static final Logger LOG = Logger.getInstance(FileStorageCoreUtil.class);

  public static final String COMPONENT = "component";
  public static final String NAME = "name";
  public static final String DEFAULT_EXT = ".xml";

  @NotNull
  public static TreeMap<String, Element> load(@NotNull Element rootElement, @Nullable PathMacroSubstitutor pathMacroSubstitutor, boolean intern) {
    if (pathMacroSubstitutor != null) {
      pathMacroSubstitutor.expandPaths(rootElement);
    }

    StringInterner interner = intern ? new StringInterner() : null;
    List<Element> children = rootElement.getChildren(COMPONENT);
    if (children.isEmpty() && rootElement.getName().equals(COMPONENT) && rootElement.getAttributeValue(NAME) != null) {
      // exclusive component data
      // singleton must be not used here - later we modify list
      children = new SmartList<>(rootElement);
    }

    CompositePathMacroFilter filter = null;

    TreeMap<String, Element> map = new TreeMap<>();
    for (Iterator<Element> iterator = children.iterator(); iterator.hasNext(); ) {
      Element element = iterator.next();
      String name = getComponentNameIfValid(element);
      if (name == null || !(element.getAttributes().size() > 1 || !element.getChildren().isEmpty())) {
        continue;
      }

      // so, PathMacroFilter can easily find component name (null parent)
      iterator.remove();

      if (interner != null) {
        JDOMUtil.internElement(element, interner);
      }

      map.put(name, element);

      if (pathMacroSubstitutor instanceof TrackingPathMacroSubstitutor) {
        if (filter == null) {
          filter = new CompositePathMacroFilter(PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME.getExtensions());
        }

        ((TrackingPathMacroSubstitutor)pathMacroSubstitutor)
          .addUnknownMacros(name, PathMacrosCollector.getMacroNames(element, filter, PathMacrosImpl.getInstanceEx()));
      }

      // remove only after "getMacroNames" - some PathMacroFilter requires element name attribute
      element.removeAttribute(NAME);
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
      if (parent == null || !(parent instanceof Element)) {
        break;
      }

      componentElement = (Element)parent;
    }

    return StringUtil.nullize(componentElement.getAttributeValue(NAME));
  }
}
