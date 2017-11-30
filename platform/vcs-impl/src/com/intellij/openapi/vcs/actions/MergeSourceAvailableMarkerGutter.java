/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.annotate.AnnotationSource;
import com.intellij.openapi.vcs.annotate.AnnotationSourceSwitcher;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.Consumer;

import java.awt.*;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
class MergeSourceAvailableMarkerGutter extends AnnotationFieldGutter implements Consumer<AnnotationSource> {
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
    return switcher.mergeSourceAvailable(line) ? "M" : "";
  }

  @Override
  public void consume(final AnnotationSource annotationSource) {
    myTurnedOn = annotationSource.showMerged();
  }
}
