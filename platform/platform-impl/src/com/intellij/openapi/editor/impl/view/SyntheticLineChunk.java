// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import java.util.List;

final class SyntheticLineChunk extends LineChunk {
  SyntheticLineChunk(int startOffset, int endOffset, List<LineFragment> fragments) {
    super(startOffset, endOffset, fragments);
  }

  @Override
  boolean isReal() {
    return false;
  }
}
