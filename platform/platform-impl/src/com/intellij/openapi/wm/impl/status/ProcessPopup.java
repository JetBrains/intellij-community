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
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.MinimizeButton;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalBox;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class ProcessPopup  {

  private final VerticalBox myProcessBox = new VerticalBox();

  private final InfoAndProgressPanel myProgressPanel;

  private JBPopup myPopup;

  private JPanel myActiveFocusedContent;
  private JComponent myActiveContentComponent;

  private final JLabel myInactiveContentComponent;

  private final Wrapper myRootContent = new Wrapper();

  private final Set<InlineProgressIndicator> myIndicators = new HashSet<>();

  public ProcessPopup(final InfoAndProgressPanel progressPanel) {
    myProgressPanel = progressPanel;

    buildActiveContent();
    myInactiveContentComponent = new JLabel(IdeBundle.message("progress.window.empty.text"), null, JLabel.CENTER) {
      public Dimension getPreferredSize() {
        return getEmptyPreferredSize();
      }
    };
    myInactiveContentComponent.setFocusable(true);

    switchToPassive();
  }

  public void addIndicator(InlineProgressIndicator indicator) {
    myIndicators.add(indicator);

    myProcessBox.add(indicator.getComponent());
    myProcessBox.add(Box.createVerticalStrut(4));

    swithToActive();

    revalidateAll();
  }

  public void removeIndicator(InlineProgressIndicator indicator) {
    if (indicator.getComponent().getParent() != myProcessBox) return;

    removeExtraSeparator(indicator);
    myProcessBox.remove(indicator.getComponent());

    myIndicators.remove(indicator);
    switchToPassive();

    revalidateAll();
  }

  private void swithToActive() {
    if (myActiveContentComponent.getParent() == null && myIndicators.size() > 0) {
      myRootContent.removeAll();
      myRootContent.setContent(myActiveContentComponent);
    }
  }

  private void switchToPassive() {
    if (myInactiveContentComponent.getParent() == null && myIndicators.size() == 0) {
      myRootContent.removeAll();
      myRootContent.setContent(myInactiveContentComponent);
    }
  }

  private void removeExtraSeparator(final InlineProgressIndicator indicator) {
    final Component[] all = myProcessBox.getComponents();
    final int index = ArrayUtil.indexOf(all, indicator.getComponent());
    if (index == -1) return;


    if (index == 0 && all.length > 1) {
      myProcessBox.remove(1);
    } else if (all.length > 2 && index < all.length - 1) {
      myProcessBox.remove(index + 1);
    }

    myProcessBox.remove(indicator.getComponent());
  }

  public void show(boolean requestFocus) {
    JComponent toFocus = myRootContent.getTargetComponent() == myActiveContentComponent ? myActiveFocusedContent : myInactiveContentComponent;

    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(myRootContent, toFocus);
    builder.addListener(new JBPopupAdapter() {
      public void onClosed(LightweightWindowEvent event) {
        myProgressPanel.hideProcessPopup();
      }
    });
    builder.setMovable(true);
    builder.setResizable(true);
    builder.setTitle(IdeBundle.message("progress.window.title"));
    builder.setDimensionServiceKey(null, "ProcessPopupWindow", true);
    builder.setCancelOnClickOutside(false);
    builder.setRequestFocus(requestFocus);
    builder.setBelongsToGlobalPopupStack(false);
    builder.setLocateByContent(true);

    builder.setCancelButton(new MinimizeButton("Hide"));

    JFrame frame = (JFrame)UIUtil.findUltimateParent(myProgressPanel);

    updateContentUI();
    myActiveContentComponent.setBorder(null);
    if (frame != null) {
      Dimension contentSize = myRootContent.getPreferredSize();
      Rectangle bounds = frame.getBounds();
      int width = Math.max(bounds.width / 4, contentSize.width);
      int height = Math.min(bounds.height / 4, contentSize.height);

      int x = (int)(bounds.getMaxX() - width);
      int y = (int)(bounds.getMaxY() - height);
      myPopup = builder.createPopup();

      StatusBarEx sb = (StatusBarEx)((IdeFrame)frame).getStatusBar();
      if (sb.isVisible()) {
        y -= sb.getSize().height;
      }

      myPopup.showInScreenCoordinates(myProgressPanel.getRootPane(), new Point(x - 5, y - 5));
    } else {
      myPopup = builder.createPopup();
      myPopup.showInCenterOf(myProgressPanel.getRootPane());
    }
  }

  private void buildActiveContent() {
    myActiveFocusedContent = new ActiveContent();

    final JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(myProcessBox, BorderLayout.NORTH);

    myActiveFocusedContent.add(wrapper, BorderLayout.CENTER);

    final JScrollPane scrolls = new JBScrollPane(myActiveFocusedContent) {
      public Dimension getPreferredSize() {
        if (myProcessBox.getComponentCount() > 0) {
          return super.getPreferredSize();
        } else {
          return getEmptyPreferredSize();
        }
      }
    };
    myActiveContentComponent = scrolls;
    updateContentUI();
  }

  private void updateContentUI() {
    if (myActiveContentComponent == null || myActiveFocusedContent == null) return;
    IJSwingUtilities.updateComponentTreeUI(myActiveContentComponent);
    if (myActiveContentComponent instanceof JScrollPane) {
      ((JScrollPane)myActiveContentComponent).getViewport().setBackground(myActiveFocusedContent.getBackground());
    }
    myActiveContentComponent.setBorder(null);

  }

  private static Dimension getEmptyPreferredSize() {
    final Dimension size = ScreenUtil.getMainScreenBounds().getSize();
    size.width *= 0.3d;
    size.height *= 0.3d;
    return size;
  }

  public void hide() {
    if (myPopup != null) {
      final JBPopup popup = myPopup;
      myPopup = null;
      popup.cancel();
    }
  }

  public boolean isShowing() {
    return myPopup != null;
  }


  private class ActiveContent extends JPanel implements Scrollable {

    private final JLabel myLabel = new JLabel("XXX");

    public ActiveContent() {
      super(new BorderLayout());
      setBorder(DialogWrapper.ourDefaultBorder);
      setFocusable(true);
    }


    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    public int getScrollableUnitIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
      return myLabel.getPreferredSize().height;
    }

    public int getScrollableBlockIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
      return myLabel.getPreferredSize().height;
    }

    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  private void revalidateAll() {
    myRootContent.revalidate();
    myRootContent.repaint();
  }

}
