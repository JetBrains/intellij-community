// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.stripe;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.RegionPainter;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

/**
 * @author Sergey.Malenkov
 */
public abstract class Updater<Painter extends ErrorStripePainter> implements Disposable {
  private final Painter myPainter;
  private final JScrollBar myScrollBar;
  private final MergingUpdateQueue myQueue;
  private final MouseAdapter myMouseAdapter = new MouseAdapter() {
    @Override
    public void mouseMoved(MouseEvent event) {
      onMouseMove(myPainter, event.getX(), event.getY());
    }

    @Override
    public void mouseClicked(MouseEvent event) {
      onMouseClick(myPainter, event.getX(), event.getY());
    }
  };

  protected Updater(@NotNull Painter painter, JScrollPane pane) {
    this(painter, pane.getVerticalScrollBar());
  }

  protected Updater(@NotNull Painter painter, JScrollBar bar) {
    myPainter = painter;
    myScrollBar = bar;
    myScrollBar.addMouseListener(myMouseAdapter);
    myScrollBar.addMouseMotionListener(myMouseAdapter);
    myQueue = new MergingUpdateQueue("ErrorStripeUpdater", 100, true, myScrollBar, this);
    UIUtil.putClientProperty(myScrollBar, JBScrollBar.TRACK, new RegionPainter<Object>() {
      @Override
      public void paint(Graphics2D g, int x, int y, int width, int height, Object object) {
        DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
        myPainter.setMinimalThickness(settings == null ? 2 : Math.min(settings.getErrorStripeMarkMinHeight(), JBUIScale.scale(4)));
        myPainter.setErrorStripeGap(Registry.intValue("error.stripe.gap", 0));
        if (myPainter instanceof ExtraErrorStripePainter) {
          ExtraErrorStripePainter extra = (ExtraErrorStripePainter)myPainter;
          extra.setGroupSwap(!myScrollBar.getComponentOrientation().isLeftToRight());
        }
        myPainter.paint(g, x, y, width, height, object);
      }
    });
  }

  @Override
  public void dispose() {
    myScrollBar.removeMouseListener(myMouseAdapter);
    myScrollBar.removeMouseMotionListener(myMouseAdapter);
    UIUtil.putClientProperty(myScrollBar, JBScrollBar.TRACK, null);
  }

  private int findErrorStripeIndex(Painter painter, int x, int y) {
    int index = painter.findIndex(x, y);
    if (null != painter.getErrorStripe(index)) return index;
    index = painter.findIndex(x, y + 1);
    if (null != painter.getErrorStripe(index)) return index;
    index = painter.findIndex(x, y - 1);
    if (null != painter.getErrorStripe(index)) return index;
    index = painter.findIndex(x, y + 2);
    if (null != painter.getErrorStripe(index)) return index;
    return -1;
  }

  protected void onMouseMove(Painter painter, int x, int y) {
    onMouseMove(painter, findErrorStripeIndex(painter, x, y));
  }

  protected void onMouseMove(Painter painter, int index) {
    myScrollBar.setCursor(index < 0 ? null : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  protected void onMouseClick(Painter painter, int x, int y) {
    onMouseClick(painter, findErrorStripeIndex(painter, x, y));
  }

  protected void onMouseClick(Painter painter, int index) {
    onSelect(painter, index);
  }

  protected void onSelect(Painter painter, int index) {
  }

  protected ShortcutSet getNextErrorShortcut() {
    return getActiveKeymapShortcuts("GotoNextError");
  }

  public void selectNext(int index) {
    onSelect(myPainter, findNextIndex(index));
  }

  protected ShortcutSet getPreviousErrorShortcut() {
    return getActiveKeymapShortcuts("GotoPreviousError");
  }

  public void selectPrevious(int index) {
    onSelect(myPainter, findPreviousIndex(index));
  }

  protected abstract void update(Painter painter);

  protected void update(Painter painter, int index, Object object) {
    painter.setErrorStripe(index, getErrorStripe(object));
  }

  protected ErrorStripe getErrorStripe(Object object) {
    return object instanceof ErrorStripe ? (ErrorStripe)object : null;
  }

  public final void update() {
    myQueue.cancelAllUpdates();
    myQueue.queue(new Update("update") {
      @Override
      public void run() {
        update(myPainter);
        if (myPainter.isModified()) {
          myScrollBar.invalidate();
          myScrollBar.repaint();
        }
      }
    });
  }

  public int findNextIndex(int current) {
    SearchResult result = new SearchResult();
    int max = myPainter.getErrorStripeCount() - 1;
    if (0 <= current && current < max) {
      result.updateForward(myPainter, current + 1, max);
      result.updateForward(myPainter, 0, current);
    }
    else if (0 <= max) {
      result.updateForward(myPainter, 0, max);
    }
    return result.index;
  }

  public int findPreviousIndex(int current) {
    SearchResult result = new SearchResult();
    int max = myPainter.getErrorStripeCount() - 1;
    if (0 < current && current <= max) {
      result.updateBackward(myPainter, current - 1, 0);
      result.updateBackward(myPainter, max, current);
    }
    else if (0 <= max) {
      result.updateBackward(myPainter, max, 0);
    }
    return result.index;
  }

  private static final class SearchResult {
    int layer = 0;
    int index = -1;

    void updateForward(ErrorStripePainter painter, int index, int max) {
      while (index <= max) update(painter, index++);
    }

    void updateBackward(ErrorStripePainter painter, int index, int min) {
      while (min <= index) update(painter, index--);
    }

    void update(ErrorStripePainter painter, int index) {
      ErrorStripe stripe = painter.getErrorStripe(index);
      if (stripe != null) {
        int layer = stripe.getLayer();
        if (layer > this.layer) {
          this.layer = layer;
          this.index = index;
        }
      }
    }
  }
}
