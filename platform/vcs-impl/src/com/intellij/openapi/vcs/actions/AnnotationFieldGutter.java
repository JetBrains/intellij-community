/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutterAction;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.vcs.annotate.AnnotationListener;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

class AnnotationFieldGutter implements ActiveAnnotationGutter {
  protected final FileAnnotation myAnnotation;
  private final Editor myEditor;
  protected final LineAnnotationAspect myAspect;
  private final TextAnnotationPresentation myPresentation;
  private final AnnotationListener myListener;
  private final boolean myIsGutterAction;

  AnnotationFieldGutter(FileAnnotation annotation, Editor editor, LineAnnotationAspect aspect, final TextAnnotationPresentation presentation) {
    myAnnotation = annotation;
    myEditor = editor;
    myAspect = aspect;
    myPresentation = presentation;
    myIsGutterAction = myAspect instanceof EditorGutterAction;

    myListener = new AnnotationListener() {
      public void onAnnotationChanged() {
        myEditor.getGutter().closeAllAnnotations();
      }
    };

    myAnnotation.addListener(myListener);
  }

  public boolean isGutterAction() {
    return myIsGutterAction;
  }

  public String getLineText(int line, Editor editor) {
    return myAspect.getValue(line);
  }

  @Nullable
  public String getToolTip(final int line, final Editor editor) {
    return XmlStringUtil.escapeString(myAnnotation.getToolTip(line));
  }

  public void doAction(int line) {
    if (myIsGutterAction) {
      ((EditorGutterAction)myAspect).doAction(line);
    }
  }

  public Cursor getCursor(final int line) {
    if (myIsGutterAction) {
      return ((EditorGutterAction)myAspect).getCursor(line);
    } else {
      return Cursor.getDefaultCursor();
    }

  }

  public EditorFontType getStyle(final int line, final Editor editor) {
    return myPresentation.getFontType(line);
  }

  @Nullable
  public ColorKey getColor(final int line, final Editor editor) {
    return myPresentation.getColor(line);
  }

  public List<AnAction> getPopupActions(final Editor editor) {
    return myPresentation.getActions();
  }

  public void gutterClosed() {
    myAnnotation.removeListener(myListener);
    myAnnotation.dispose();
    myEditor.getUserData(AnnotateToggleAction.KEY_IN_EDITOR).remove(this);
  }
}
