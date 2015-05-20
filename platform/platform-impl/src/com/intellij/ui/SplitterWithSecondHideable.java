/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.ui.PseudoSplitter;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.changes.RefreshablePanel;
import com.intellij.util.OnOffListener;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/22/11
 * Time: 2:33 PM
 */
public abstract class SplitterWithSecondHideable {
  private final PseudoSplitter mySplitter;
  private final AbstractTitledSeparatorWithIcon myTitledSeparator;
  private final boolean myVertical;
  private final OnOffListener<Integer> myListener;
  private final JPanel myFictivePanel;
  private Splitter.DividerImpl mySuperDivider;
  private float myPreviousProportion;

  public SplitterWithSecondHideable(final boolean vertical,
                                    final String separatorText,
                                    final JComponent firstComponent,
                                    final OnOffListener<Integer> listener) {
    myVertical = vertical;
    myListener = listener;
    myFictivePanel = new JPanel(new BorderLayout());
    Icon icon;
    Icon openIcon;
    if (vertical) {
      icon = AllIcons.General.ComboArrow;
      openIcon = AllIcons.General.ComboUpPassive;
    }
    else {
      icon = AllIcons.General.ComboArrowRight;
      openIcon = AllIcons.General.ComboArrowRightPassive;
    }

    myTitledSeparator = new AbstractTitledSeparatorWithIcon(icon, openIcon, separatorText) {
      @Override
      protected RefreshablePanel createPanel() {
        return createDetails();
      }

      @Override
      protected void initOnImpl() {
        final float proportion = myPreviousProportion > 0 ? myPreviousProportion : getSplitterInitialProportion();
        mySplitter.setSecondComponent(myDetailsComponent.getPanel());
        mySuperDivider.setResizeEnabled(true);

        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            mySplitter.fixFirst(proportion);
            mySplitter.invalidate();
            mySplitter.validate();
            mySplitter.repaint();
          }
        });
      }

      @Override
      protected void onImpl() {
        final float proportion = myPreviousProportion > 0 ? myPreviousProportion : getSplitterInitialProportion();
        final int firstSize = vertical ? mySplitter.getFirstComponent().getHeight() : mySplitter.getFirstComponent().getWidth();
        // !! order is important! first fix
        mySplitter.fixFirst();
        myListener.on((int)((1 - proportion) * firstSize / proportion));
        //mySplitter.setProportion(proportion);
        mySplitter.setSecondComponent(myDetailsComponent.getPanel());
        mySplitter.revalidate();
        mySplitter.repaint();
        mySuperDivider.setResizeEnabled(true);
      }

      @Override
      protected void offImpl() {
        final int previousSize = vertical ? mySplitter.getSecondComponent().getHeight() : mySplitter.getSecondComponent().getWidth();
        mySplitter.setSecondComponent(myFictivePanel);
        myPreviousProportion = mySplitter.getProportion();
        mySplitter.freeAll();
        mySplitter.setProportion(1.0f);
        mySplitter.revalidate();
        mySplitter.repaint();
        myListener.off(previousSize);
        mySuperDivider.setResizeEnabled(false);
      }
    };
    mySplitter = new PseudoSplitter(vertical) {
      {
        myTitledSeparator.mySeparator.addMouseListener(new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent e) {
            myTitledSeparator.mySeparator
              .setCursor(myTitledSeparator.myOn ? new Cursor(Cursor.S_RESIZE_CURSOR) : new Cursor(Cursor.DEFAULT_CURSOR));
            ((MyDivider)mySuperDivider).processMouseEvent(e);
          }

          @Override
          public void mouseExited(MouseEvent e) {
            myTitledSeparator.mySeparator.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            ((MyDivider)mySuperDivider).processMouseEvent(e);
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            ((MyDivider)mySuperDivider).processMouseEvent(e);
          }

          @Override
          public void mousePressed(MouseEvent e) {
            ((MyDivider)mySuperDivider).processMouseEvent(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            ((MyDivider)mySuperDivider).processMouseEvent(e);
          }

          @Override
          public void mouseWheelMoved(MouseWheelEvent e) {
            ((MyDivider)mySuperDivider).processMouseEvent(e);
          }

          @Override
          public void mouseDragged(MouseEvent e) {
            ((MyDivider)mySuperDivider).processMouseEvent(e);
          }

          @Override
          public void mouseMoved(MouseEvent e) {
            ((MyDivider)mySuperDivider).processMouseEvent(e);
          }
        });

        myTitledSeparator.mySeparator.addMouseMotionListener(new MouseMotionListener() {
          @Override
          public void mouseDragged(MouseEvent e) {
            ((MyDivider)mySuperDivider).processMouseMotionEvent(e);
          }

          @Override
          public void mouseMoved(MouseEvent e) {
            ((MyDivider)mySuperDivider).processMouseMotionEvent(e);
          }
        });
      }

      @Override
      protected Divider createDivider() {
        mySuperDivider = new MyDivider();
        mySuperDivider.add(myTitledSeparator,
                           new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                  new Insets(0, 0, 0, 0), 0, 0));
        return mySuperDivider;
      }

      @Override
      public int getDividerWidth() {
        return vertical ? myTitledSeparator.getHeight() : myTitledSeparator.getWidth();
      }

      class MyDivider extends DividerImpl {
        @Override
        public void processMouseMotionEvent(MouseEvent e) {
          super.processMouseMotionEvent(e);
        }

        @Override
        public void processMouseEvent(MouseEvent e) {
          super.processMouseEvent(e);
        }
      }
    };
    mySplitter.setDoubleBuffered(true);
    mySplitter.setFirstComponent(firstComponent);
    mySplitter.setSecondComponent(myFictivePanel);
    //mySplitter.setShowDividerIcon(false);
    mySplitter.setProportion(1.0f);
  }

  public void setText(final String value) {
    myTitledSeparator.setText(value);
  }

  public void setEnabledColor(final boolean enabled) {
    myTitledSeparator.myLabel.setForeground(enabled ? UIUtil.getActiveTextColor() : UIUtil.getInactiveTextColor());
  }

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
}
