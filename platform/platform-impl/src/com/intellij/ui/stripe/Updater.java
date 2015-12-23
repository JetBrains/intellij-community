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
package com.intellij.ui.stripe;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.RegionPainter;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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
    myPainter = painter;
    myScrollBar = pane.getVerticalScrollBar();
    myScrollBar.addMouseListener(myMouseAdapter);
    myScrollBar.addMouseMotionListener(myMouseAdapter);
    myQueue = new MergingUpdateQueue("ErrorStripeUpdater", 100, true, myScrollBar, this);
    UIUtil.putClientProperty(myScrollBar, JBScrollBar.TRACK, new RegionPainter<Object>() {
      @Override
      public void paint(Graphics2D g, int x, int y, int width, int height, Object object) {
        DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
        myPainter.setMinimalThickness(settings == null ? 2 : Math.min(settings.ERROR_STRIPE_MARK_MIN_HEIGHT, JBUI.scale(4)));
        myPainter.setErrorStripeGap(Registry.intValue("error.stripe.gap", 0));
        if (myPainter instanceof ExtraErrorStripePainter) {
          ExtraErrorStripePainter extra = (ExtraErrorStripePainter)myPainter;
          extra.setGroupSwap(!myScrollBar.getComponentOrientation().isLeftToRight());
        }
        myPainter.paint(g, x, y, width, height, object);
      }
    });
    Disposer.register(this, new TranslucencyThumbPainter(myPainter, myScrollBar));
    Disposer.register(this, new TranslucencyThumbPainter(null, pane.getHorizontalScrollBar()));
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
    return new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts("GotoNextError"));
  }

  public void selectNext(int index) {
    onSelect(myPainter, findNextIndex(index));
  }

  protected ShortcutSet getPreviousErrorShortcut() {
    return new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts("GotoPreviousError"));
  }

  public void selectPrevious(int index) {
    onSelect(myPainter, findPreviousIndex(index));
  }

  protected abstract void update(Painter painter);

  protected void update(Painter painter, int index, Object object) {
    painter.setErrorStripe(index, getErrorStripe(object));
  }

  protected ErrorStripe getErrorStripe(Object object) {
    return null;
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
    int count = myPainter.getErrorStripeCount();
    int foundIndex = -1;
    int foundLayer = 0;
    if (0 <= current && current < count) {
      current++;
      for (int index = current; index < count; index++) {
        int layer = getLayer(index);
        if (layer > foundLayer) {
          foundIndex = index;
          foundLayer = layer;
        }
      }
      for (int index = 0; index < current; index++) {
        int layer = getLayer(index);
        if (layer > foundLayer) {
          foundIndex = index;
          foundLayer = layer;
        }
      }
    }
    else {
      for (int index = 0; index < count; index++) {
        int layer = getLayer(index);
        if (layer > foundLayer) {
          foundIndex = index;
          foundLayer = layer;
        }
      }
    }
    return foundIndex;
  }

  public int findPreviousIndex(int current) {
    int count = myPainter.getErrorStripeCount();
    int foundIndex = -1;
    int foundLayer = 0;
    if (0 <= current && current < count) {
      current--;
      for (int index = count - 1; index >= 0; index++) {
        int layer = getLayer(index);
        if (layer > foundLayer) {
          foundIndex = index;
          foundLayer = layer;
        }
      }
      for (int index = current - 1; index >= 0; index++) {
        int layer = getLayer(index);
        if (layer > foundLayer) {
          foundIndex = index;
          foundLayer = layer;
        }
      }
    }
    else {
      for (int index = count - 1; index >= 0; index--) {
        int layer = getLayer(index);
        if (layer > foundLayer) {
          foundIndex = index;
          foundLayer = layer;
        }
      }
    }
    return foundIndex;
  }

  private int getLayer(int index) {
    ErrorStripe stripe = myPainter.getErrorStripe(index);
    return stripe == null ? -1 : stripe.getLayer();
  }
}
