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
package com.intellij.openapi.diff.impl.incrementalMerge.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.splitter.DividerPolygon;
import com.intellij.openapi.diff.impl.util.DiffDivider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * The container for an {@link Editor}, which is added then to {@link com.intellij.openapi.diff.impl.util.ThreePanels}.
 */
public class EditorPlace extends JComponent implements Disposable, EditorEx.RepaintCallback {
  private static final Logger LOG = Logger.getInstance(EditorPlace.class);

  @NotNull private final MergePanel2.DiffEditorState myState;
  @NotNull private final MergePanelColumn myColumn;
  @NotNull private final MergePanel2 myMergePanel;
  @NotNull private final ArrayList<EditorListener> myListeners = new ArrayList<EditorListener>();
  @Nullable private EditorEx myEditor;

  public EditorPlace(@NotNull MergePanel2.DiffEditorState state, @NotNull MergePanelColumn column, @NotNull MergePanel2 mergePanel) {
    myState = state;
    myColumn = column;
    myMergePanel = mergePanel;

    setLayout(new BorderLayout());
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    paintThis(g);
  }

  public void call(Graphics g) {
    repaintScrollbar();
  }

  private void repaintScrollbar() {
    if (myEditor == null || myColumn != MergePanelColumn.BASE) {
      return; // we draw above the scrollbar only in the central column
    }
    Component editorComponent = myEditor.getComponent();
    JScrollBar scrollBar = myEditor.getScrollPane().getVerticalScrollBar();
    repaint(editorComponent.getWidth() - scrollBar.getWidth(), 0, scrollBar.getWidth(), scrollBar.getHeight());
  }

  private void paintThis(Graphics g) {
    if (myEditor != null) {
      ArrayList<DividerPolygon> polygons = DividerPolygon.createVisiblePolygons(myMergePanel.getSecondEditingSide(), FragmentSide.SIDE1,
                                                                                DiffDivider.MERGE_DIVIDER_POLYGONS_OFFSET);
      for (DividerPolygon polygon : polygons) {
        int startY = polygon.getTopLeftY();
        int endY = polygon.getBottomLeftY();
        int height = endY - startY;

        if (height == 0) { // draw at least a one-pixel line (e.g. for insertion or deletion), as it is done in highlighters
          height = 1;
        }

        drawPolygonAboveScrollBar((Graphics2D)g, startY, height, polygon.getColor(), polygon.isApplied());
      }
    }
  }

  private void drawPolygonAboveScrollBar(@NotNull Graphics2D g, int startY, int height, @NotNull Color color, boolean applied) {
    // painting only above the central scrollbar, because painting on edge scrollbars is not needed, and there are error stripes
    if (myColumn != MergePanelColumn.BASE) {
      return;
    }

    g.setColor(color);
    JScrollBar scrollBar = myEditor.getScrollPane().getVerticalScrollBar();
    int startX = scrollBar.getX();
    int endX = startX + scrollBar.getWidth() - 1;
    if (!applied) {
      if (height > 2) {
        g.fillRect(startX, startY, scrollBar.getWidth(), height);
        UIUtil.drawFramingLines(g, startX, endX, startY, startY + height, DividerPolygon.FRAMING_LINE_COLOR);
      }
      else {
        UIUtil.drawFramingLines(g, startX, endX, startY, startY + height, color);
      }
    }
    else {
      DiffUtil.drawBoldDottedFramingLines(g, startX, endX, startY, startY + height, color);
    }
  }

  public void addNotify() {
    if (myEditor != null) {
      super.addNotify();
      return;
    }
    createEditor();
    super.addNotify();
    revalidate();
  }

  private void createEditor() {
    LOG.assertTrue(myEditor == null);
    myEditor = myState.createEditor();
    if (myEditor == null) return;
    add(myEditor.getComponent(), BorderLayout.CENTER);
    myEditor.registerScrollBarRepaintCallback(this);

    repaint();
    fireEditorCreated();
  }

  public void addListener(EditorListener listener) {
    myListeners.add(listener);
  }

  private void fireEditorCreated() {
    EditorListener[] listeners = getListeners();
    for (EditorListener listener : listeners) {
      listener.onEditorCreated(this);
    }
  }

  private void fireEditorReleased(Editor releasedEditor) {
    EditorListener[] listeners = getListeners();
    for (EditorListener listener : listeners) {
      listener.onEditorReleased(releasedEditor);
    }
  }

  private EditorListener[] getListeners() {
    EditorListener[] listeners = new EditorListener[myListeners.size()];
    myListeners.toArray(listeners);
    return listeners;
  }

  public void removeNotify() {
    removeEditor();
    super.removeNotify();
  }

  private void removeEditor() {
    if (myEditor != null) {
      myEditor.registerScrollBarRepaintCallback(null);
      Editor releasedEditor = myEditor;
      remove(myEditor.getComponent());
      getEditorFactory().releaseEditor(myEditor);
      myEditor = null;
      fireEditorReleased(releasedEditor);
    }
  }

  @Nullable
  public Editor getEditor() {
    return myEditor;
  }

  public void setDocument(Document document) {
    if (document == getDocument()) return;
    removeEditor();
    myState.setDocument(document);
    createEditor();
  }

  private Document getDocument() {
    return myState.getDocument();
  }

  @NotNull
  public MergePanel2.DiffEditorState getState() {
    return myState;
  }

  public interface EditorListener {
    void onEditorCreated(EditorPlace place);
    void onEditorReleased(Editor releasedEditor);
  }

  private static EditorFactory getEditorFactory() {
    return EditorFactory.getInstance();
  }

  @Nullable
  public JComponent getContentComponent() {
    return myEditor == null ? null : myEditor.getContentComponent();
  }

  public void dispose() {
    removeEditor();
  }
}
