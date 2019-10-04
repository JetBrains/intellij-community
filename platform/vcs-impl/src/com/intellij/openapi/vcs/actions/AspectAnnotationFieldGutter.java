/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

/**
 * @author Irina Chernushina
 * @author Konstantin Bulenkov
 */
public class AspectAnnotationFieldGutter extends AnnotationFieldGutter {
  @NotNull protected final LineAnnotationAspect myAspect;
  private final boolean myIsGutterAction;

  public AspectAnnotationFieldGutter(@NotNull FileAnnotation annotation,
                                     @NotNull LineAnnotationAspect aspect,
                                     @NotNull TextAnnotationPresentation presentation,
                                     @Nullable Couple<Map<VcsRevisionNumber, Color>> colorScheme) {
    super(annotation, presentation, colorScheme);
    myAspect = aspect;
    myIsGutterAction = myAspect instanceof EditorGutterAction;
  }

  @Override
  public boolean isGutterAction() {
    return myIsGutterAction;
  }

  @Override
  public String getLineText(int line, Editor editor) {
    final String value = isAvailable() ? myAspect.getValue(line) : "";
    if (myAspect.getId() == LineAnnotationAspect.AUTHOR) {
      return ShortNameType.shorten(value, ShowShortenNames.getType());
    }
    return value;
  }

  @Nullable
  @Override
  public String getToolTip(final int line, final Editor editor) {
    return isAvailable() ? myAnnotation.getHtmlToolTip(line) : null;
  }

  @Override
  public void doAction(int line) {
    if (myIsGutterAction) {
      ((EditorGutterAction)myAspect).doAction(line);
    }
  }

  @Override
  public Cursor getCursor(final int line) {
    if (myIsGutterAction) {
      return ((EditorGutterAction)myAspect).getCursor(line);
    }
    return super.getCursor(line);
  }

  @Override
  public boolean isShowByDefault() {
    return myAspect.isShowByDefault();
  }

  @Nullable
  @Override
  public String getID() {
    return myAspect.getId();
  }
}
