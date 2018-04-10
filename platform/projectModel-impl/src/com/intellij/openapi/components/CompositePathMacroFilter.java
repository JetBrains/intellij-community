// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.application.PathMacroFilter;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CompositePathMacroFilter extends PathMacroFilter {
  private final PathMacroFilter[] myFilters;

  public CompositePathMacroFilter(PathMacroFilter[] filters) {
    myFilters = filters;
  }

  @Override
  public boolean skipPathMacros(@NotNull Element element) {
    for (PathMacroFilter filter : myFilters) {
      if (filter.skipPathMacros(element)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean skipPathMacros(Text element) {
    for (PathMacroFilter filter : myFilters) {
      if (filter.skipPathMacros(element)) return true;
    }
    return false;
  }

  @Override
  public boolean skipPathMacros(@NotNull Attribute attribute) {
    for (PathMacroFilter filter : myFilters) {
      if (filter.skipPathMacros(attribute)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean recursePathMacros(Text element) {
    for (PathMacroFilter filter : myFilters) {
      if (filter.recursePathMacros(element)) return true;
    }
    return false;
  }

  @Override
  public boolean recursePathMacros(Attribute attribute) {
    for (PathMacroFilter filter : myFilters) {
      if (filter.recursePathMacros(attribute)) return true;
    }
    return false;
  }
}
