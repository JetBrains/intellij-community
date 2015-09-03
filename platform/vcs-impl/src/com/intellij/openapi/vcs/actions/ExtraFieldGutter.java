/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;

import java.awt.*;
import java.util.Map;

/**
 * The only reason for this column is IDEA-60573
 *
 * @author Konstantin Bulenkov
 */
public class ExtraFieldGutter extends AnnotationFieldGutter  {
  private final AnnotateActionGroup myActionGroup;

  public ExtraFieldGutter(FileAnnotation fileAnnotation,
                          AnnotationPresentation presentation,
                          Couple<Map<VcsRevisionNumber, Color>> bgColorMap, AnnotateActionGroup actionGroup) {
    super(fileAnnotation, null, presentation, bgColorMap);
    myActionGroup = actionGroup;
  }

  @Override
  public String getLineText(int line, Editor editor) {
    return isAvailable() ? " " : "";
  }

  @Override
  public boolean isAvailable() {
    for (AnAction action : myActionGroup.getChildren(null)) {
      if (action instanceof ShowHideAspectAction && ((ShowHideAspectAction)action).isSelected(null)) {
        return false;
      }
    }
    return true;
  }
}
