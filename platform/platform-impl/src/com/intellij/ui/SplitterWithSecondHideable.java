// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.ui.PseudoSplitter;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.changes.RefreshablePanel;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI.Panels;
import com.intellij.util.ui.MouseEventHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Objects;

import static com.intellij.icons.AllIcons.General.ArrowDown;
import static com.intellij.icons.AllIcons.General.ArrowRight;

public abstract class SplitterWithSecondHideable {
  public interface OnOffListener {
    void on(int hideableHeight);
    void off(int hideableHeight);
  }

  @NotNull private final PseudoSplitter mySplitter;
  @NotNull private final MyTitledSeparator myTitledSeparator;
  @NotNull private final OnOffListener myListener;
  @NotNull private final JPanel myFictivePanel;
  private float myPreviousProportion;

  public SplitterWithSecondHideable(boolean vertical,
                                    @NotNull @NlsContexts.Separator String separatorText,
                                    @NotNull JComponent firstComponent,
                                    @NotNull OnOffListener listener) {
    myListener = listener;
    myFictivePanel = Panels.simplePanel();
    myTitledSeparator = new MyTitledSeparator(separatorText, vertical);
    mySplitter = new MySplitter(vertical);
    mySplitter.setDoubleBuffered(true);
    mySplitter.setHonorComponentsMinimumSize(false);
    mySplitter.setFirstComponent(firstComponent);
    mySplitter.setSecondComponent(myFictivePanel);
    mySplitter.setProportion(1.0f);
  }

  public void setText(@NlsContexts.Separator String value) {
    myTitledSeparator.setText(value);
  }

  @NotNull
  public Splitter getComponent() {
    return mySplitter;
  }

  protected abstract RefreshablePanel createDetails();

  protected abstract float getSplitterInitialProportion();

  public float getUsedProportion() {
    return isOn() ? mySplitter.getProportion() : myPreviousProportion;
  }

  public void initOn() {
    myTitledSeparator.initOn();
  }

  public void setInitialProportion() {
    myTitledSeparator.setInitialProportion();
  }

  public void on() {
    myTitledSeparator.on();
  }

  public void off() {
    myTitledSeparator.off();
  }

  public boolean isOn() {
    return myTitledSeparator.myOn;
  }

  private class MyTitledSeparator extends AbstractTitledSeparatorWithIcon {
    MyTitledSeparator(@NotNull @NlsContexts.Separator String separatorText, boolean vertical) {
      super(ArrowRight, vertical ? ArrowDown : Objects.requireNonNull(IconLoader.getDisabledIcon(ArrowRight)), separatorText);
    }

    @Override
    protected RefreshablePanel createPanel() {
      return createDetails();
    }

    @Override
    protected void initOnImpl() {
      mySplitter.setSecondComponent(myDetailsComponent.getPanel());
      mySplitter.setResizeEnabled(true);
    }

    public void setInitialProportion() {
      float proportion = myPreviousProportion > 0 ? myPreviousProportion : getSplitterInitialProportion();
      mySplitter.fixFirst(proportion);
      mySplitter.invalidate();
      mySplitter.validate();
      mySplitter.repaint();
    }

    @Override
    protected void onImpl() {
      float proportion = myPreviousProportion > 0 ? myPreviousProportion : getSplitterInitialProportion();
      int firstSize = mySplitter.isVertical() ? mySplitter.getFirstComponent().getHeight() : mySplitter.getFirstComponent().getWidth();
      // !! order is important! first fix
      mySplitter.fixFirst();
      myListener.on((int)((1 - proportion) * firstSize / proportion));
      mySplitter.setSecondComponent(myDetailsComponent.getPanel());
      mySplitter.revalidate();
      mySplitter.repaint();
      mySplitter.setResizeEnabled(true);
    }

    @Override
    protected void offImpl() {
      int previousSize = mySplitter.isVertical() ? mySplitter.getSecondComponent().getHeight() : mySplitter.getSecondComponent().getWidth();
      mySplitter.setSecondComponent(myFictivePanel);
      myPreviousProportion = mySplitter.getProportion();
      mySplitter.freeAll();
      mySplitter.setProportion(1.0f);
      mySplitter.revalidate();
      mySplitter.repaint();
      myListener.off(previousSize);
      mySplitter.setResizeEnabled(false);
    }
  }

  private class MySplitter extends PseudoSplitter {
    @NotNull private final MouseEventHandler myMouseListener = new MouseEventHandler() {
      @Override
      public void mouseEntered(MouseEvent event) {
        myTitledSeparator.mySeparator.setCursor(new Cursor(isOn() ? Cursor.S_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR));
        super.mouseEntered(event);
      }

      @Override
      public void mouseExited(MouseEvent event) {
        myTitledSeparator.mySeparator.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        super.mouseExited(event);
      }

      @Override
      protected void handle(MouseEvent event) {
        if (event.getID() == MouseEvent.MOUSE_DRAGGED || event.getID() == MouseEvent.MOUSE_MOVED) {
          ((MyDivider)myDivider).processMouseMotionEvent(event);
        }
        else {
          ((MyDivider)myDivider).processMouseEvent(event);
        }
      }
    };

    MySplitter(boolean vertical) {
      super(vertical);
      myTitledSeparator.mySeparator.addMouseListener(myMouseListener);
      myTitledSeparator.mySeparator.addMouseMotionListener(myMouseListener);
    }

    @Override
    protected Divider createDivider() {
      MyDivider divider = new MyDivider();
      divider.add(myTitledSeparator,
                  new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                         JBInsets.emptyInsets(),
                                         0, 0));
      return divider;
    }

    @Override
    public int getDividerWidth() {
      return isVertical() ? myTitledSeparator.getHeight() : myTitledSeparator.getWidth();
    }

    private class MyDivider extends DividerImpl {
      @Override
      public void processMouseMotionEvent(MouseEvent e) {
        super.processMouseMotionEvent(e);
      }

      @Override
      public void processMouseEvent(MouseEvent e) {
        super.processMouseEvent(e);
      }
    }
  }
}
