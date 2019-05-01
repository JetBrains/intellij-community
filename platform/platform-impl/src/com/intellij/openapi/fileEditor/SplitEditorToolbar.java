// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

public class SplitEditorToolbar extends JPanel implements Disposable {
  @Nullable private final MySpacingPanel mySpacingPanel;

  private final ActionToolbar myRightToolbar;

  private final List<EditorGutterComponentEx> myGutters = new ArrayList<>();

  private final ComponentAdapter myAdjustToGutterListener = new ComponentAdapter() {
    @Override
    public void componentResized(ComponentEvent e) {
      adjustSpacing();
    }

    @Override
    public void componentShown(ComponentEvent e) {
      adjustSpacing();
    }

    @Override
    public void componentHidden(ComponentEvent e) {
      adjustSpacing();
    }
  };

  public SplitEditorToolbar(@Nullable ActionToolbar leftToolbar, @NotNull ActionToolbar rightToolbar) {
    super(new GridBagLayout());
    myRightToolbar = rightToolbar;

    if (leftToolbar != null) {
      mySpacingPanel = new MySpacingPanel((int)leftToolbar.getComponent().getPreferredSize().getHeight());
      add(mySpacingPanel);
      add(leftToolbar.getComponent());
    }
    else {
      mySpacingPanel = null;
    }

    final JPanel centerPanel = new JPanel(new BorderLayout());
    centerPanel.add(new JLabel(ApplicationBundle.message("preview.splitter.toolbar.view.label"), SwingConstants.RIGHT), BorderLayout.EAST);
    add(centerPanel,
        new GridBagConstraints(2, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0));
    add(myRightToolbar.getComponent());

    setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIUtil.CONTRAST_BORDER_COLOR));

    addComponentListener(myAdjustToGutterListener);

    if (leftToolbar != null) leftToolbar.updateActionsImmediately();
    rightToolbar.updateActionsImmediately();
  }

  public void addGutterToTrack(@NotNull EditorGutterComponentEx gutterComponentEx) {
    myGutters.add(gutterComponentEx);

    gutterComponentEx.addComponentListener(myAdjustToGutterListener);
  }

  public void refresh() {
    adjustSpacing();
    myRightToolbar.updateActionsImmediately();
  }

  private void adjustSpacing() {
    if (mySpacingPanel == null) return;

    EditorGutterComponentEx leftMostGutter = null;
    for (EditorGutterComponentEx gutter : myGutters) {
      if (!gutter.isShowing()) {
        continue;
      }
      if (leftMostGutter == null || leftMostGutter.getX() > gutter.getX()) {
        leftMostGutter = gutter;
      }
    }

    final int spacing;
    if (leftMostGutter == null) {
      spacing = 0;
    }
    else {
      spacing = leftMostGutter.getWhitespaceSeparatorOffset();
    }
    mySpacingPanel.setSpacing(spacing);

    revalidate();
    repaint();
  }

  @Override
  public void dispose() {
    removeComponentListener(myAdjustToGutterListener);
    for (EditorGutterComponentEx gutter : myGutters) {
      gutter.removeComponentListener(myAdjustToGutterListener);
    }
  }

  private static class MySpacingPanel extends JPanel {
    private final int myHeight;

    private int mySpacing;

    MySpacingPanel(int height) {
      myHeight = height;
      mySpacing = 0;
      setOpaque(false);
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(mySpacing, myHeight);
    }

    public void setSpacing(int spacing) {
      mySpacing = spacing;
    }
  }
}
