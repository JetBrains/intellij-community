// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.editor.IndentsModel;

import java.util.List;

public final class EmptyIndentsModel implements IndentsModel {
  @Override
  public IndentGuideDescriptor getCaretIndentGuide() {
    return null;
  }

  @Override
  public IndentGuideDescriptor getDescriptor(int startLine, int endLine) {
    return null;
  }

  @Override
  public void assumeIndents(List<IndentGuideDescriptor> descriptors) {
  }
}
