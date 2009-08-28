package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import java.util.List;

public interface TextAnnotationPresentation {
  EditorFontType getFontType(int line);
  ColorKey getColor(int line);
  List<AnAction> getActions();
}
