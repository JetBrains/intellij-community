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
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Irina Chernushina
 * @author Konstantin Bulenkov
 */
class AnnotationFieldGutter implements ActiveAnnotationGutter {
  protected final FileAnnotation myAnnotation;
  private final Editor myEditor;
  protected final LineAnnotationAspect myAspect;
  private final TextAnnotationPresentation myPresentation;
  private final AnnotationListener myListener;
  private final boolean myIsGutterAction;
  private Map<String, Color> myColorScheme;
  private boolean myShowBg = true;

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

  public List<AnAction> getPopupActions(int line, final Editor editor) {
    return myPresentation.getActions(line);
  }

  public void gutterClosed() {
    myAnnotation.removeListener(myListener);
    myAnnotation.dispose();    
    final Collection<ActiveAnnotationGutter> gutters = myEditor.getUserData(AnnotateToggleAction.KEY_IN_EDITOR);
    if (gutters != null) {
      gutters.remove(this);
    }
  }

  @Nullable
  public Color getBgColor(int line, Editor editor) {
    if (myColorScheme == null || !myShowBg) return null;
    final String s = getLineText(line, editor);
    if (s == null) return null;
    final Color bg = myColorScheme.get(s);
    return bg == null ? findBgColor(s) : bg;
  }

  @Nullable
  private Color findBgColor(String s) {
    if (myColorScheme != null) {
      for (String key : myColorScheme.keySet()) {
            if (key.startsWith(s)) {
              return myColorScheme.get(key);
            }
          }
    }
    return null;
  }

  public void setAspectValueToBgColorMap(Map<String, Color> colorScheme) {
    myColorScheme = colorScheme;
  }

  public void setShowBg(boolean show) {
    myShowBg = show;
  }
}
