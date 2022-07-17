// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.impl.EmptyIndentsModel;
import com.intellij.openapi.editor.impl.EmptySoftWrapModel;
import com.intellij.openapi.editor.impl.SettingsImpl;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;


public class TextComponentEditorImpl extends UserDataHolderBase implements TextComponentEditor {
  private final Project myProject;
  private final JTextComponent myTextComponent;
  private final TextComponentDocument myDocument;
  private final TextComponentCaretModel myCaretModel;
  private final TextComponentSelectionModel mySelectionModel;
  private final TextComponentScrollingModel myScrollingModel;
  private final EmptySoftWrapModel mySoftWrapModel;
  private final TextComponentFoldingModel myFoldingModel;
  private EditorSettings mySettings;

  public TextComponentEditorImpl(final Project project, @NotNull JTextComponent textComponent) {
    myProject = project;
    myTextComponent = textComponent;
    if (textComponent instanceof JTextArea) {
      myDocument = new TextAreaDocument((JTextArea) textComponent);
    }
    else {
      myDocument = new TextComponentDocument(textComponent);
    }
    myCaretModel = new TextComponentCaretModel(this);
    mySelectionModel = new TextComponentSelectionModel(this);
    myScrollingModel = new TextComponentScrollingModel(textComponent);
    mySoftWrapModel = new EmptySoftWrapModel();
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
  public JTextComponent getContentComponent() {
    return myTextComponent;
  }

  @Override
  public void setBorder(@Nullable Border border) {
  }

  @Override
  public Insets getInsets() {
    return JBInsets.emptyInsets();
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

  @NotNull
  @Override
  public InlayModel getInlayModel() {
    return new TextComponentInlayModel();
  }

  @NotNull
  @Override
  public EditorKind getEditorKind() {
    return EditorKind.UNTYPED;
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

  @NotNull
  @Override
  public Point2D visualPositionToPoint2D(@NotNull VisualPosition pos) {
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

  @NotNull
  @Override
  public VisualPosition xyToVisualPosition(@NotNull Point2D p) {
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
    return true;
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
