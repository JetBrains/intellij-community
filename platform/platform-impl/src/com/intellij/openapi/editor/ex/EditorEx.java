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
package com.intellij.openapi.editor.ex;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;

public interface EditorEx extends Editor {
  @NonNls String PROP_INSERT_MODE = "insertMode";
  @NonNls String PROP_COLUMN_MODE = "columnMode";
  @NonNls String PROP_FONT_SIZE = "fontSize";
  Key<TextRange> LAST_PASTED_REGION = Key.create("LAST_PASTED_REGION");

  EditorGutterComponentEx getGutterComponentEx();

  EditorHighlighter getHighlighter();

  void setHighlighter(EditorHighlighter highlighter);

  void setColorsScheme(@NotNull EditorColorsScheme scheme);

  void setInsertMode(boolean val);

  void setColumnMode(boolean val);

  void setLastColumnNumber(int val);

  int getLastColumnNumber();

  int VERTICAL_SCROLLBAR_LEFT = 0;
  int VERTICAL_SCROLLBAR_RIGHT = 1;

  void setVerticalScrollbarOrientation(int type);

  void setVerticalScrollbarVisible(boolean b);

  void setHorizontalScrollbarVisible(boolean b);

  CutProvider getCutProvider();

  CopyProvider getCopyProvider();

  PasteProvider getPasteProvider();

  DeleteProvider getDeleteProvider();

  void repaint(int startOffset, int endOffset);

  void reinitSettings();

  void addPropertyChangeListener(PropertyChangeListener listener);

  void removePropertyChangeListener(PropertyChangeListener listener);

  int getMaxWidthInRange(int startOffset, int endOffset);

  void stopOptimizedScrolling();

  boolean setCaretVisible(boolean b);

  boolean setCaretEnabled(boolean enabled);

  void addFocusListener(FocusChangeListener listener);

  void setOneLineMode(boolean b);

  JScrollPane getScrollPane();

  boolean isRendererMode();

  void setRendererMode(boolean isRendererMode);

  boolean isUnderRepainting();

  void setFile(VirtualFile vFile);

  DataContext getDataContext();

  boolean processKeyTyped(KeyEvent e);

  void setFontSize(int fontSize);

  Color getBackgroundColor();

  void setBackgroundColor(Color color);

  Dimension getContentSize();

  boolean isEmbeddedIntoDialogWrapper();
  void setEmbeddedIntoDialogWrapper(boolean b);

  VirtualFile getVirtualFile();

  int calcColumnNumber(CharSequence text, int start, int offset, int tabSize);

  int calcColumnNumber(int offset, int lineIndex);
}
