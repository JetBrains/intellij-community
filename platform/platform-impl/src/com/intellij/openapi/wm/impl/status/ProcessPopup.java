// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

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
      @Override
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
    builder.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
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
      myPopup = builder.addUserData("SIMPLE_WINDOW").createPopup();
      myPopup.getContent().putClientProperty(AbstractPopup.FIRST_TIME_SIZE, new JBDimension(400, 0));

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

    myActiveContentComponent = new JBScrollPane(myActiveFocusedContent) {
      @Override
      public Dimension getPreferredSize() {
        if (myProcessBox.getComponentCount() > 0) {
          return super.getPreferredSize();
        } else {
          return getEmptyPreferredSize();
        }
      }
    };
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


  private static class ActiveContent extends JPanel implements Scrollable {

    private final JLabel myLabel = new JLabel("XXX");

    ActiveContent() {
      super(new BorderLayout());
      setBorder(DialogWrapper.createDefaultBorder());
      setFocusable(true);
    }


    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
      return myLabel.getPreferredSize().height;
    }

    @Override
    public int getScrollableBlockIncrement(final Rectangle visibleRect, final int orientation, final int direction) {
      return myLabel.getPreferredSize().height;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  private void revalidateAll() {
    myRootContent.revalidate();
    myRootContent.repaint();
  }

}
