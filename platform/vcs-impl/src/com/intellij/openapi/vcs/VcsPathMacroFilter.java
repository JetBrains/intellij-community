// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.util.xmlb.Constants;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

final class VcsPathMacroFilter extends PathMacroFilter {
  @Override
  public boolean skipPathMacros(@NotNull Attribute attribute) {
    final Element parent = attribute.getParent();
    final String parentName = parent.getName();
    if ("MESSAGE".equals(parentName) && Constants.VALUE.equals(attribute.getName())) {
      return true;
    }
    if (Constants.OPTION.equals(parentName) && "LAST_COMMIT_MESSAGE".equals(parent.getAttributeValue(Constants.NAME))) {
      return true;
    }

    // ignore comment field of ChangeListManager.list and in general any attribute named "comment" since in most cases value should be stored as is
    if ("comment".equals(attribute.getName())) {
      return true;
    }
    return false;
  }
}
