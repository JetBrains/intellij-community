package com.intellij.openapi.components;

import org.jdom.Element;

public interface PathMacroSubstitutor {
  String expandPath(String path);
  String collapsePath(String path);

  void expandPaths(Element element);
  void collapsePaths(Element element);
}
