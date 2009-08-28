package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.util.ui.TreeWithEmptyText;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

public abstract class LinkMouseListenerBase extends MouseAdapter implements MouseMotionListener {
  @Nullable
  protected abstract Object getTagAt(final MouseEvent e);

  public void mouseClicked(final MouseEvent e) {
    if (!e.isPopupTrigger() && e.getButton() == 1) {
      Object tag = getTagAt(e);
      handleTagClick(tag);
    }
  }

  protected void handleTagClick(final Object tag) {
    if (tag instanceof Runnable) {
      ((Runnable) tag).run();
    }
  }
  
  public void mouseDragged(MouseEvent e) {
  }

  public void mouseMoved(MouseEvent e) {
    Component tree = (Component)e.getSource();
    if (tree instanceof TreeWithEmptyText && ((TreeWithEmptyText) tree).isModelEmpty()) return;
    Object tag = getTagAt(e);
    if (tag != null) {
      tree.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    else {
      tree.setCursor(Cursor.getDefaultCursor());
    }
  }

  public void install(final JComponent tree) {
    tree.addMouseListener(this);
    tree.addMouseMotionListener(this);
  }
}
