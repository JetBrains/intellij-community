// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;

public enum AnnotationSource {
  LOCAL(false),
  MERGE(true);

  private final boolean myShowMerged;

  AnnotationSource(boolean showMerged) {
    myShowMerged = showMerged;
  }

  public boolean showMerged() {
    return myShowMerged;
  }

  public ColorKey getColor(boolean lastCommit) {
    return lastCommit ? EditorColors.ANNOTATIONS_LAST_COMMIT_COLOR : EditorColors.ANNOTATIONS_COLOR;
  }

  public ColorKey getColor() {
    return getColor(false);
  }

  public static AnnotationSource getInstance(final boolean showMerged) {
    return showMerged ? MERGE : LOCAL;
  }
}
