// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.annotate.AnnotationSource;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;

import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;

/**
 * shown additionally only when merge
 *
 * @author Konstantin Bulenkov
 */
final class CurrentRevisionAnnotationFieldGutter extends AspectAnnotationFieldGutter implements Consumer<AnnotationSource> {
  // merge source showing is turned on
  private boolean myTurnedOn;

  CurrentRevisionAnnotationFieldGutter(FileAnnotation annotation,
                                       LineAnnotationAspect aspect,
                                       TextAnnotationPresentation highlighting,
                                       Couple<Map<VcsRevisionNumber, Color>> colorScheme) {
    super(annotation, aspect, highlighting, colorScheme);
  }

  @Override
  public ColorKey getColor(int line, Editor editor) {
    return AnnotationSource.LOCAL.getColor();
  }

  @Override
  public String getLineText(int line, Editor editor) {
    final String value = myAspect.getValue(line);
    if (String.valueOf(myAnnotation.getLineRevisionNumber(line)).equals(value)) {
      return "";
    }
    // shown in merge sources mode
    return myTurnedOn ? value : "";
  }

  @Override
  public String getToolTip(int line, Editor editor) {
    final String aspectTooltip = myAspect.getTooltipText(line);
    if (aspectTooltip != null) {
      return aspectTooltip;
    }
    final String text = getLineText(line, editor);
    return ((text == null) || (text.length() == 0)) ? "" : VcsBundle.message("annotation.original.revision.text", text);
  }

  @Override
  public void accept(AnnotationSource annotationSource) {
    myTurnedOn = annotationSource.showMerged();
  }
}
