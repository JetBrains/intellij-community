// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

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
  public @NotNull Document getDocument() {
    return myDocument;
  }

  @Override
  public boolean isViewer() {
    return !myTextComponent.isEditable();
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myTextComponent;
  }

  @Override
  public @NotNull JTextComponent getContentComponent() {
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
  public @NotNull TextComponentSelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  @Override
  public @NotNull MarkupModel getMarkupModel() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public @NotNull FoldingModel getFoldingModel() {
    return myFoldingModel;
  }

  @Override
  public @NotNull ScrollingModel getScrollingModel() {
    return myScrollingModel;
  }

  @Override
  public @NotNull CaretModel getCaretModel() {
    return myCaretModel;
  }

  @Override
  public @NotNull SoftWrapModel getSoftWrapModel() {
    return mySoftWrapModel;
  }

  @Override
  public @NotNull InlayModel getInlayModel() {
    return new EmptyInlayModel();
  }

  @Override
  public @NotNull EditorKind getEditorKind() {
    return EditorKind.UNTYPED;
  }

  @Override
  public @NotNull EditorSettings getSettings() {
    if (mySettings == null) {
      mySettings = new SettingsImpl();
    }
    return mySettings;
  }

  @Override
  public @NotNull EditorColorsScheme getColorsScheme() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public int getLineHeight() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public @NotNull Point logicalPositionToXY(final @NotNull LogicalPosition pos) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public int logicalPositionToOffset(final @NotNull LogicalPosition pos) {
    if (pos.line >= myDocument.getLineCount()) {
      return myDocument.getTextLength();
    }
    return myDocument.getLineStartOffset(pos.line) + pos.column;
  }

  @Override
  public @NotNull VisualPosition logicalToVisualPosition(final @NotNull LogicalPosition logicalPos) {
    return new VisualPosition(logicalPos.line, logicalPos.column);
  }

  @Override
  public @NotNull Point visualPositionToXY(final @NotNull VisualPosition visible) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public @NotNull Point2D visualPositionToPoint2D(@NotNull VisualPosition pos) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public @NotNull LogicalPosition visualToLogicalPosition(final @NotNull VisualPosition visiblePos) {
    return new LogicalPosition(visiblePos.line, visiblePos.column);
  }

  @Override
  public @NotNull LogicalPosition offsetToLogicalPosition(final int offset) {
    int line = myDocument.getLineNumber(offset);
    final int lineStartOffset = myDocument.getLineStartOffset(line);
    return new LogicalPosition(line, offset - lineStartOffset);
  }

  @Override
  public @NotNull VisualPosition offsetToVisualPosition(final int offset) {
    int line = myDocument.getLineNumber(offset);
    final int lineStartOffset = myDocument.getLineStartOffset(line);
    return new VisualPosition(line, offset - lineStartOffset);
  }

  @Override
  public @NotNull VisualPosition offsetToVisualPosition(int offset, boolean leanForward, boolean beforeSoftWrap) {
    return offsetToVisualPosition(offset);
  }

  @Override
  public @NotNull LogicalPosition xyToLogicalPosition(final @NotNull Point p) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public @NotNull VisualPosition xyToVisualPosition(final @NotNull Point p) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public @NotNull VisualPosition xyToVisualPosition(@NotNull Point2D p) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void addEditorMouseListener(final @NotNull EditorMouseListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void removeEditorMouseListener(final @NotNull EditorMouseListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void addEditorMouseMotionListener(final @NotNull EditorMouseMotionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void removeEditorMouseMotionListener(final @NotNull EditorMouseMotionListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public boolean isDisposed() {
    return false;
  }

  @Override
  public @Nullable Project getProject() {
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
  public @NotNull EditorGutter getGutter() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public @Nullable EditorMouseEventArea getMouseEventArea(final @NotNull MouseEvent e) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void setHeaderComponent(final @Nullable JComponent header) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public boolean hasHeaderComponent() {
    return false;
  }

  @Override
  public @Nullable JComponent getHeaderComponent() {
    return null;
  }

  @Override
  public @NotNull IndentsModel getIndentsModel() {
    return new EmptyIndentsModel();
  }

  private static final AtomicBoolean classesLoaded = new AtomicBoolean();

  @ApiStatus.Internal
  public static void ensureRequiredClassesAreLoaded() {
    if (!classesLoaded.compareAndSet(false, true)) {
      return;
    }
    var classLoader = TextComponentEditorImpl.class.getClassLoader();
    for (var c : Arrays.asList(
      Border.class,
      CaretModel.class,
      Document.class,
      EditorColorsScheme.class,
      EditorGutter.class,
      EditorKind.class,
      EditorMouseEventArea.class,
      EditorMouseListener.class,
      EditorMouseMotionListener.class,
      EditorSettings.class,
      EmptyIndentsModel.class,
      EmptyInlayModel.class,
      EmptySoftWrapModel.class,
      FoldingModel.class,
      IndentsModel.class,
      InlayModel.class,
      Insets.class,
      JBInsets.class,
      JComponent.class,
      JTextArea.class,
      JTextComponent.class,
      LogicalPosition.class,
      MarkupModel.class,
      MouseEvent.class,
      Point.class,
      Point2D.class,
      Project.class,
      ScrollingModel.class,
      SettingsImpl.class,
      SoftWrapModel.class,
      TextAreaDocument.class,
      TextComponentCaretModel.class,
      TextComponentDocument.class,
      TextComponentEditor.class,
      TextComponentFoldingModel.class,
      TextComponentScrollingModel.class,
      TextComponentSelectionModel.class,
      UserDataHolderBase.class,
      VisualPosition.class
    )) {
      try {
        Class.forName(c.getName(), true, classLoader);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
