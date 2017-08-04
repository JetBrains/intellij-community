/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.ui.PseudoSplitter;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.changes.RefreshablePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.Panels;
import com.intellij.util.ui.MouseEventHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

import static com.intellij.icons.AllIcons.General.*;

public abstract class SplitterWithSecondHideable {
  public interface OnOffListener<T> {
    void on(T t);
    void off(T t);
  }

  @NotNull private final PseudoSplitter mySplitter;
  @NotNull private final AbstractTitledSeparatorWithIcon myTitledSeparator;
  @NotNull private final OnOffListener<Integer> myListener;
  @NotNull private final JPanel myFictivePanel;
  private float myPreviousProportion;

  public SplitterWithSecondHideable(boolean vertical,
                                    @NotNull String separatorText,
                                    @NotNull JComponent firstComponent,
                                    @NotNull OnOffListener<Integer> listener) {
    myListener = listener;
    myFictivePanel = Panels.simplePanel();
    myTitledSeparator = new MyTitledSeparator(separatorText, vertical);
    mySplitter = new MySplitter(vertical);
    mySplitter.setDoubleBuffered(true);
    mySplitter.setFirstComponent(firstComponent);
    mySplitter.setSecondComponent(myFictivePanel);
    mySplitter.setProportion(1.0f);
  }

  public void setText(String value) {
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
    public MyTitledSeparator(@NotNull String separatorText, boolean vertical) {
      super(ComboArrowRight, vertical ? ComboArrowDown : ComboArrowRightPassive, separatorText);
    }

    @Override
    protected RefreshablePanel createPanel() {
      return createDetails();
    }

    @Override
    protected void initOnImpl() {
      float proportion = myPreviousProportion > 0 ? myPreviousProportion : getSplitterInitialProportion();
      mySplitter.setSecondComponent(myDetailsComponent.getPanel());
      mySplitter.setResizeEnabled(true);

      SwingUtilities.invokeLater(() -> {
        mySplitter.fixFirst(proportion);
        mySplitter.invalidate();
        mySplitter.validate();
        mySplitter.repaint();
      });
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

    public MySplitter(boolean vertical) {
      super(vertical);
      myTitledSeparator.mySeparator.addMouseListener(myMouseListener);
      myTitledSeparator.mySeparator.addMouseMotionListener(myMouseListener);
    }

    @Override
    protected Divider createDivider() {
      MyDivider divider = new MyDivider();
      divider.add(myTitledSeparator,
                  new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(),
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
