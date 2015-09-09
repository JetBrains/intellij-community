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
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;

import java.awt.*;
import java.util.Map;

class HighlightedAdditionalColumn extends AnnotationFieldGutter {

  HighlightedAdditionalColumn(FileAnnotation annotation,
                              LineAnnotationAspect aspect,
                              TextAnnotationPresentation presentation,
                              Couple<Map<VcsRevisionNumber, Color>> colorScheme) {
    super(annotation, aspect, presentation, colorScheme);
  }

  @Override
  public String getLineText(int line, Editor editor) {
    VcsRevisionNumber revision = myAnnotation.originalRevision(line);
    VcsRevisionNumber currentRevision = myAnnotation.getCurrentRevision();
    return currentRevision != null && currentRevision.equals(revision) ? "*" : "";
  }
}
