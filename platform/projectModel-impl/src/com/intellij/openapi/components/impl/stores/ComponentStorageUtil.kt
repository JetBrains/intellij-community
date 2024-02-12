// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components.impl.stores;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.components.CompositePathMacroFilter;
import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.LineSeparator;
import com.intellij.util.SmartList;
import kotlin.Pair;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Text;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@ApiStatus.Internal
public final class ComponentStorageUtil {
  private static final Logger LOG = Logger.getInstance(ComponentStorageUtil.class);

  public static final String COMPONENT = "component";
  public static final String NAME = "name";
  public static final String DEFAULT_EXT = ".xml";

  public static @NotNull Map<String, Element> load(@NotNull Element rootElement, @Nullable PathMacroSubstitutor pathMacroSubstitutor) {
    if (pathMacroSubstitutor != null) {
      pathMacroSubstitutor.expandPaths(rootElement);
    }

    var children = rootElement.getChildren(COMPONENT);
    if (children.isEmpty() && rootElement.getName().equals(COMPONENT) && rootElement.getAttributeValue(NAME) != null) {
      children = new SmartList<>(rootElement);  // must be modifiable
    }

    var map = new TreeMap<String, Element>();

    CompositePathMacroFilter filter = null;
    for (var iterator = children.iterator(); iterator.hasNext(); ) {
      var element = iterator.next();
      var name = getComponentNameIfValid(element);
      if (name == null || (element.getAttributes().size() <= 1 && element.getContent().isEmpty())) {
        continue;
      }

      // so, PathMacroFilter can easily find component name (null parent)
      iterator.remove();

      if (pathMacroSubstitutor instanceof TrackingPathMacroSubstitutor ts && !isKotlinSerializable(element)) {
        if (filter == null) {
          filter = new CompositePathMacroFilter(PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME.getExtensionList());
        }
        ts.addUnknownMacros(name, PathMacrosCollector.getMacroNames(element, filter, PathMacrosImpl.getInstanceEx()));
      }

      // remove only after "getMacroNames" - some PathMacroFilter requires element name attribute
      element.removeAttribute(NAME);

      map.put(name, element);
    }

    return map;
  }

  public static @NotNull Pair<Map<String, Element>, Map<String, @Nullable LineSeparator>> load(
    @NotNull Path dir,
    @Nullable PathMacroSubstitutor pathMacroSubstitutor
  ) throws IOException {
    try (var files = Files.newDirectoryStream(dir)) {
      var fileToState = new HashMap<String, Element>();
      var fileToSeparator = new HashMap<String, @Nullable LineSeparator>();

      for (var file : files) {
        // ignore system files like .DS_Store on Mac
        if (!Strings.endsWithIgnoreCase(file.toString(), DEFAULT_EXT)) {
          continue;
        }

        try {
          var elementLineSeparatorPair = load(Files.readAllBytes(file));
          var element = elementLineSeparatorPair.component1();
          var componentName = getComponentNameIfValid(element);
          if (componentName == null) continue;

          if (!element.getName().equals(COMPONENT)) {
            LOG.error("Incorrect root tag name (" + element.getName() + ") in " + file);
            continue;
          }

          var elementChildren = element.getChildren();
          if (elementChildren.isEmpty()) continue;

          var state = elementChildren.get(0).detach();
          if (JDOMUtil.isEmpty(state)) {
            continue;
          }

          if (pathMacroSubstitutor != null) {
            pathMacroSubstitutor.expandPaths(state);
            if (pathMacroSubstitutor instanceof TrackingPathMacroSubstitutor ts) {
              ts.addUnknownMacros(componentName, PathMacrosCollector.getMacroNames(state));
            }
          }

          var name = file.getFileName().toString();
          fileToState.put(name, state);
          fileToSeparator.put(name, elementLineSeparatorPair.component2());
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

      return new Pair<>(fileToState, fileToSeparator);
    }
    catch (DirectoryIteratorException e) {
      throw e.getCause();
    }
    catch (NoSuchFileException | NotDirectoryException ignore) {
      return new Pair<>(Map.of(), Map.of());
    }
  }

  private static boolean isKotlinSerializable(Element element) {
    if (element.hasAttributes()) return false;
    var content = element.getContent();
    return content.size() == 1 && content.get(0) instanceof Text;
  }

  private static @Nullable String getComponentNameIfValid(Element element) {
    var name = element.getAttributeValue(NAME);
    if (!Strings.isEmpty(name)) return name;
    LOG.warn("No name attribute for component in " + JDOMUtil.writeElement(element));
    return null;
  }

  public static @NotNull Pair<Element, @Nullable LineSeparator> load(byte @NotNull [] data) throws IOException, JDOMException {
    var offset = CharsetToolkit.getBOMLength(data, StandardCharsets.UTF_8);
    var text = new String(data, offset, data.length - offset, StandardCharsets.UTF_8);
    var element = JDOMUtil.load(text);
    var lineSeparator = detectLineSeparator(text);
    return new Pair<>(element, lineSeparator);
  }

  private static @Nullable LineSeparator detectLineSeparator(CharSequence chars) {
    for (int i = 0; i < chars.length(); i++) {
      var c = chars.charAt(i);
      if (c == '\r') return LineSeparator.CRLF;
      if (c == '\n') return LineSeparator.LF;  // if we are here, there was no '\r' before
    }
    return null;
  }
}
