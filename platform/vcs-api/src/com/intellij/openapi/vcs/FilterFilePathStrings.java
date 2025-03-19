// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import java.util.Collections;
import java.util.List;

public final class FilterFilePathStrings extends AbstractFilterChildren<String> {
  private static final FilterFilePathStrings ourInstance = new FilterFilePathStrings();

  public static FilterFilePathStrings getInstance() {
    return ourInstance;
  }

  @Override
  protected void sortAscending(List<? extends String> list) {
    Collections.sort(list);
  }

  @Override
  protected boolean isAncestor(String parent, String child) {
    return child.startsWith(parent);
  }
}
