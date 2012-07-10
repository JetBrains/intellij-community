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
import com.intellij.openapi.diff.impl.EditingSides;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.splitter.DividerPolygon;
import com.intellij.openapi.diff.impl.util.DiffDivider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * The container for an {@link Editor}, which is added then to {@link com.intellij.openapi.diff.impl.util.ThreePanels}.
 */
public class EditorPlace extends JComponent implements Disposable {
  private static final Logger LOG = Logger.getInstance(EditorPlace.class);

  @NotNull private final MergePanel2.DiffEditorState myState;
  @NotNull private final MergePanelColumn myColumn;
  @NotNull private final SideInfo mySideInfo;
  @NotNull private final ArrayList<EditorListener> myListeners = new ArrayList<EditorListener>();

  @Nullable private Editor myEditor;

  private final VisibleAreaListener myVisibleAreaListener = new VisibleAreaListener() {
    public void visibleAreaChanged(VisibleAreaEvent e) {
      repaint();
    }
  };

  public EditorPlace(@NotNull MergePanel2.DiffEditorState state, @NotNull MergePanelColumn column, @NotNull MergePanel2 mergePanel) {
    myState = state;
    myColumn = column;
    mySideInfo = SideInfo.convertFromColumn(mergePanel, myColumn);

    setLayout(new BorderLayout());
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);

    if (myEditor != null) {
      Graphics2D g2 = (Graphics2D)g;
      drawAbove(true, g2, mySideInfo.createPolygonsForGutter(), mySideInfo.isTakeLeftSideOfPolygonForGutter());
      drawAbove(false, g2, mySideInfo.createPolygonsForScrollbar(), mySideInfo.isTakeLeftSideOfPolygonForScrollbar());
    }
  }

  private void drawAbove(boolean gutter, Graphics2D g2, ArrayList<DividerPolygon> polygons, boolean takeLeftSideOfPolygon) {
    for (DividerPolygon polygon : polygons) {
      int startY = takeLeftSideOfPolygon ? polygon.getTopLeftY() :polygon.getTopRightY();
      int endY = takeLeftSideOfPolygon ? polygon.getBottomLeftY() : polygon.getBottomRightY();
      int height = endY - startY;

      if (height == 0) { // draw at least a one-pixel line (e.g. for insertion or deletion), as it is done in highlighters
        height = 1;
      }

      if (gutter) {
        drawAboveGutter(g2, startY, height, polygon.getColor(), polygon.isApplied());
      }
      else {
        drawAboveScrollBar(g2, startY, height, polygon.getColor(), polygon.isApplied());
      }
    }
  }

  private void drawAboveGutter(@NotNull Graphics2D g, int startY, int height, @NotNull Color color, boolean applied) {
    EditorGutterComponentEx gutter = ((EditorEx)myEditor).getGutterComponentEx();
    g.setColor(color);
    if (((EditorEx)myEditor).getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_RIGHT) {
      // scrollbar is at the right => the gutter is at the left (central editor case)
      int startX = gutter.getX();
      if (!applied) {
        g.fillRect(startX, startY, gutter.getWidth() + 1, height);
        drawFramingLines(g, startX, startY, startX + gutter.getWidth(), startY + height);
      }
      else {
        drawBoldDottedFramingLines(g, startX, gutter.getWidth() + 1, startY, height, color);
      }
    }
    else {
      JComponent editorComponent = myEditor.getComponent();
      int startX = editorComponent.getX() + editorComponent.getWidth() - gutter.getWidth() - 1;
      if (!applied) {
        g.fillRect(startX, startY, gutter.getWidth() + 1, height);
        drawFramingLines(g, startX, startY, startX + gutter.getWidth(), startY + height);
      }
      else {
        int endX = startX + gutter.getWidth() + 1;
        drawBoldDottedFramingLines(g, startX, endX, startY, height, color);
      }
    }
  }

  private static void drawBoldDottedFramingLines(Graphics2D g, int startX, int endX, int startY, int height, Color color) {
    UIUtil.drawBoldDottedLine(g, startX, endX, startY, null, color, false);
    UIUtil.drawBoldDottedLine(g, startX, endX, startY + height, null, color, false);
  }

  private void drawAboveScrollBar(@NotNull Graphics2D g, int startY, int height, @NotNull Color color, boolean applied) {
    // painting only above the central scrollbar, because painting on edge scrollbars is not needed, and there are error stripes
    if (myColumn == MergePanelColumn.BASE) {
      g.setColor(color);
      JScrollBar scrollBar = ((EditorEx)myEditor).getScrollPane().getVerticalScrollBar();
      int startX = scrollBar.getX();
      int endX = startX + scrollBar.getWidth();
      if (!applied) {
        g.fillRect(startX, startY, scrollBar.getWidth(), height);
        drawFramingLines(g, startX, startY, endX, startY + height);
      }
      else {
        drawBoldDottedFramingLines(g, startX, endX, startY, height, color);
      }
    }
  }

  private static void drawFramingLines(@NotNull Graphics2D g, int startX, int topY, int endX, int bottomY) {
    UIUtil.drawLine(g, startX, topY, endX, topY, null, DividerPolygon.FRAMING_LINE_COLOR);
    UIUtil.drawLine(g, startX, bottomY, endX, bottomY, null, DividerPolygon.FRAMING_LINE_COLOR);
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
    myEditor.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    myEditor.getCaretModel().addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(CaretEvent e) {
        repaint();
      }
    });
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
      myEditor.getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
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

  /**
   * Helper structure to encapsulate the legacy-style ({@link EditingSides}, {@link FragmentSide}) information about merge columns.
   */
  private static class SideInfo {
    @NotNull private final EditingSides myEditingSidesForGutter;
    @NotNull private final FragmentSide myFragmentSideForGutter;
    private final boolean myTakeLeftSideOfPolygonForGutter;
    @NotNull private final EditingSides myEditingSidesForScrollbar;
    @NotNull private final FragmentSide myFragmentSideForScrollbar;
    private final boolean myTakeLeftSideOfPolygonForScrollbar;

    private SideInfo(@NotNull EditingSides editingSidesForGutter, @NotNull FragmentSide fragmentSideForGutter,
                     boolean takeLeftSideOfPolygonForGutter,
                     @NotNull EditingSides editingSidesForScrollbar, @NotNull FragmentSide fragmentSideForScrollbar,
                     boolean takeLeftSideOfPolygonForScrollbar) {
      myEditingSidesForGutter = editingSidesForGutter;
      myFragmentSideForGutter = fragmentSideForGutter;
      myTakeLeftSideOfPolygonForGutter = takeLeftSideOfPolygonForGutter;
      myEditingSidesForScrollbar = editingSidesForScrollbar;
      myFragmentSideForScrollbar = fragmentSideForScrollbar;
      myTakeLeftSideOfPolygonForScrollbar = takeLeftSideOfPolygonForScrollbar;
    }

    public SideInfo(EditingSides side, FragmentSide fragmentSide, boolean takeLeftSideOfPolygon) {
      this(side, fragmentSide, takeLeftSideOfPolygon, side, fragmentSide, takeLeftSideOfPolygon);
    }

    public ArrayList<DividerPolygon> createPolygonsForGutter() {
      return DividerPolygon.createVisiblePolygons(myEditingSidesForGutter, myFragmentSideForGutter,
                                                  DiffDivider.MERGE_DIVIDER_POLYGONS_OFFSET);
    }

    public ArrayList<DividerPolygon> createPolygonsForScrollbar() {
      return DividerPolygon.createVisiblePolygons(myEditingSidesForScrollbar, myFragmentSideForScrollbar,
                                                  DiffDivider.MERGE_DIVIDER_POLYGONS_OFFSET);
    }

    public boolean isTakeLeftSideOfPolygonForGutter() {
      return myTakeLeftSideOfPolygonForGutter;
    }

    public boolean isTakeLeftSideOfPolygonForScrollbar() {
      return myTakeLeftSideOfPolygonForScrollbar;
    }

    @NotNull
    private static SideInfo convertFromColumn(@NotNull MergePanel2 mergePanel, @NotNull MergePanelColumn column) {
      switch (column) {
        case LEFT:
          return new SideInfo(mergePanel.getFirstEditingSide(), FragmentSide.SIDE2, true);
        case BASE:
          return new SideInfo(mergePanel.getFirstEditingSide(), FragmentSide.SIDE2, false,
                              mergePanel.getSecondEditingSide(), FragmentSide.SIDE1, true);
        case RIGHT:
          return new SideInfo(mergePanel.getSecondEditingSide(), FragmentSide.SIDE1, false);
        default:
          throw new IllegalStateException("Incorrect column value: " + column);
      }
    }

  }

}
