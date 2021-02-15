// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.annotate.AnnotationSource;
import com.intellij.openapi.vcs.annotate.AnnotationSourceSwitcher;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;

import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Konstantin Bulenkov
 */
final class MergeSourceAvailableMarkerGutter extends AnnotationFieldGutter implements Consumer<AnnotationSource> {
  // merge source showing is turned on
  private boolean myTurnedOn;

  MergeSourceAvailableMarkerGutter(FileAnnotation annotation,
                                   TextAnnotationPresentation highlighting,
                                   Couple<Map<VcsRevisionNumber, Color>> colorScheme) {
    super(annotation, highlighting, colorScheme);
  }

  @Override
  public ColorKey getColor(int line, Editor editor) {
    return AnnotationSource.LOCAL.getColor();
  }

  @Override
  public String getLineText(int line, Editor editor) {
    if (myTurnedOn) return "";
    final AnnotationSourceSwitcher switcher = myAnnotation.getAnnotationSourceSwitcher();
    if (switcher == null) return "";
    return switcher.mergeSourceAvailable(line) ? "M" : ""; //NON-NLS
  }

  @Override
  public void accept(final AnnotationSource annotationSource) {
    myTurnedOn = annotationSource.showMerged();
  }
}
