/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.impl.EmptyIndentsModel;
import com.intellij.openapi.editor.impl.SettingsImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author yole
 */
public class TextComponentEditor extends UserDataHolderBase implements Editor {
  private final Project myProject;
  private final JTextComponent myTextComponent;
  private final TextComponentDocument myDocument;
  private final TextComponentCaretModel myCaretModel;
  private final TextComponentSelectionModel mySelectionModel;
  private final TextComponentScrollingModel myScrollingModel;
  private final TextComponentSoftWrapModel mySoftWrapModel;
  private final TextComponentFoldingModel myFoldingModel;
  private EditorSettings mySettings;

  public TextComponentEditor(final Project project, @NotNull JTextComponent textComponent) {
    myProject = project;
    myTextComponent = textComponent;
    if (textComponent instanceof JTextArea) {
      myDocument = new TextAreaDocument((JTextArea) textComponent);
    }
    else {
      myDocument = new TextComponentDocument(textComponent);
    }
    myCaretModel = new TextComponentCaretModel(textComponent, this);
    mySelectionModel = new TextComponentSelectionModel(textComponent, this);
    myScrollingModel = new TextComponentScrollingModel(textComponent);
    mySoftWrapModel = new TextComponentSoftWrapModel();
    myFoldingModel = new TextComponentFoldingModel();
  }

  @Override
  @NotNull
  public Document getDocument() {
    return myDocument;
  }

  @Override
  public boolean isViewer() {
    return !myTextComponent.isEditable();
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myTextComponent;
  }

  @Override
  @NotNull
  public JComponent getContentComponent() {
    return myTextComponent;
  }

  @Override
  public void setBorder(@Nullable Border border) {
  }

  @Override
  public Insets getInsets() {
    return new Insets(0,0,0,0);
  }

  @Override
  @NotNull
  public TextComponentSelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  @Override
  @NotNull
  public MarkupModel getMarkupModel() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  @NotNull
  public FoldingModel getFoldingModel() {
    return myFoldingModel;
  }

  @Override
  @NotNull
  public ScrollingModel getScrollingModel() {
    return myScrollingModel;
  }

  @Override
  @NotNull
  public CaretModel getCaretModel() {
    return myCaretModel;
  }

  @Override
  @NotNull
  public SoftWrapModel getSoftWrapModel() {
    return mySoftWrapModel;
  }

  @Override
  @NotNull
  public EditorSettings getSettings() {
    if (mySettings == null) {
      mySettings = new SettingsImpl();
    }
    return mySettings;
  }

  @Override
  @NotNull
  public EditorColorsScheme getColorsScheme() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public int getLineHeight() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  @NotNull
  public Point logicalPositionToXY(@NotNull final LogicalPosition pos) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public int logicalPositionToOffset(@NotNull final LogicalPosition pos) {
    if (pos.line >= myDocument.getLineCount()) {
      return myDocument.getTextLength();
    }
    return myDocument.getLineStartOffset(pos.line) + pos.column;
  }

  @Override
  @NotNull
  public VisualPosition logicalToVisualPosition(@NotNull final LogicalPosition logicalPos) {
    return new VisualPosition(logicalPos.line, logicalPos.column);
  }

  @Override
  @NotNull
  public Point visualPositionToXY(@NotNull final VisualPosition visible) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull final VisualPosition visiblePos) {
    return new LogicalPosition(visiblePos.line, visiblePos.column);
  }

  @Override
  @NotNull
  public LogicalPosition offsetToLogicalPosition(final int offset) {
    int line = myDocument.getLineNumber(offset);
    final int lineStartOffset = myDocument.getLineStartOffset(line);
    return new LogicalPosition(line, offset - lineStartOffset);
  }

  @Override
  @NotNull
  public VisualPosition offsetToVisualPosition(final int offset) {
    int line = myDocument.getLineNumber(offset);
    final int lineStartOffset = myDocument.getLineStartOffset(line);
    return new VisualPosition(line, offset - lineStartOffset);
  }

  @NotNull
  @Override
  public VisualPosition offsetToVisualPosition(int offset, boolean leanForward, boolean beforeSoftWrap) {
    return offsetToVisualPosition(offset);
  }

  @Override
  @NotNull
  public LogicalPosition xyToLogicalPosition(@NotNull final Point p) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  @NotNull
  public VisualPosition xyToVisualPosition(@NotNull final Point p) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void addEditorMouseListener(@NotNull final EditorMouseListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void removeEditorMouseListener(@NotNull final EditorMouseListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void addEditorMouseMotionListener(@NotNull final EditorMouseMotionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void removeEditorMouseMotionListener(@NotNull final EditorMouseMotionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public boolean isDisposed() {
    return false;
  }

  @Override
  @Nullable
  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean isInsertMode() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public boolean isColumnMode() {
    return false;
  }

  @Override
  public boolean isOneLineMode() {
    return !(myTextComponent instanceof JTextArea);
  }

  @Override
  @NotNull
  public EditorGutter getGutter() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  @Nullable
  public EditorMouseEventArea getMouseEventArea(@NotNull final MouseEvent e) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void setHeaderComponent(@Nullable final JComponent header) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public boolean hasHeaderComponent() {
    return false;
  }

  @Override
  @Nullable
  public JComponent getHeaderComponent() {
    return null;
  }

  @NotNull
  @Override
  public IndentsModel getIndentsModel() {
    return new EmptyIndentsModel();
  }
}
