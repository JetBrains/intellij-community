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
