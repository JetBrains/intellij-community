// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.accessibility.AccessibilityUtils;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.panel.ProgressPanel;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.util.MinimizeButton;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.system.OS;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

final class ProcessPopup {
  public static final Key<ProgressPanel> KEY = new Key<>("ProgressPanel");
  static final JBDimension POPUP_MIN_SIZE = new JBDimension(300, 100);
  static final JBDimension POPUP_MIN_SIZE_WITH_BANNER = new JBDimension(464, 100);
  private static final String DIMENSION_SERVICE_KEY = "ProcessPopupWindow";

  private final InfoAndProgressPanel myProgressPanel;
  private final JBPanelWithEmptyText myIndicatorPanel;
  private final JScrollPane myContentPanel;
  private JBPopup myPopup;
  private boolean myPopupVisible;
  private final TasksFinishedDecorator myTasksFinishedDecorator;
  private final AnalyzingBannerDecorator myAnalyzingBannerDecorator;
  private final SeparatorDecorator mySeparatorDecorator;

  ProcessPopup(@NotNull InfoAndProgressPanel progressPanel) {
    myProgressPanel = progressPanel;

    myIndicatorPanel = new MyJBPanelWithEmptyText().withEmptyText(IdeBundle.message("progress.window.empty.text")).andTransparent();
    myIndicatorPanel.setBorder(JBUI.Borders.empty(10, 0, 18, 0));
    myIndicatorPanel.setFocusable(true);
    if (ExperimentalUI.isNewUI()) {
      myIndicatorPanel.setBackground(JBUI.CurrentTheme.Popup.BACKGROUND);
    }

    myTasksFinishedDecorator = new TasksFinishedDecorator(myIndicatorPanel);
    myAnalyzingBannerDecorator = new AnalyzingBannerDecorator(myIndicatorPanel, () -> myPopup, () -> {
      SeparatorDecorator.placeSeparators(myIndicatorPanel);
      revalidateAll();
    });
    mySeparatorDecorator = new SeparatorDecorator(myIndicatorPanel);

    myContentPanel = new JBScrollPane(myIndicatorPanel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    myContentPanel.setFocusCycleRoot(true);
    myContentPanel.setFocusTraversalPolicyProvider(true);
    myContentPanel.setFocusTraversalPolicy(new ProcessPopupFocusTraversalPolicy());
    updateContentUI();
  }
  
  boolean isBannerPresent() {
    return myAnalyzingBannerDecorator.isBannerPresent();
  }

  public void addIndicator(@NotNull ProgressComponent indicator) {
    JComponent component = indicator.getComponent();
    if (ExperimentalUI.isNewUI()) {
      component.setOpaque(false);
    }
    myIndicatorPanel.add(component);
    myTasksFinishedDecorator.indicatorAdded();
    myAnalyzingBannerDecorator.indicatorAdded(indicator);
    mySeparatorDecorator.indicatorAdded();
    revalidateAll();
    ensureSufficientSize();
  }

  public void removeIndicator(@NotNull ProgressComponent indicator) {
    JComponent component = indicator.getComponent();
    int index = myIndicatorPanel.getComponentZOrder(component);
    if (index == -1) {
      return;
    }

    myIndicatorPanel.remove(component);
    myTasksFinishedDecorator.indicatorRemoved();
    myAnalyzingBannerDecorator.indicatorRemoved(indicator, isShowing());
    mySeparatorDecorator.indicatorRemoved();
    revalidateAll();
    ensureSufficientSize();
  }

  /// Update the size of the popup so that the banner from [AnalyzingBannerDecorator] is well-visible:
  /// 1. Increases the minimum width of the popup if a banner is present.
  /// 2. Increases the height of the popup so the banner is fully visible.
  private void ensureSufficientSize() {
    if (myPopup == null) {
      return;
    }
    if (!myAnalyzingBannerDecorator.isBannerPresent()) {
      myPopup.setMinimumSize(POPUP_MIN_SIZE);
      return;
    }

    myPopup.setMinimumSize(POPUP_MIN_SIZE_WITH_BANNER);
    updateContentUI();

    int requiredHeight = myAnalyzingBannerDecorator.getPopupRequiredHeight();
    if (myContentPanel.getHeight() >= requiredHeight) {
      return; // the popup is tall enough already, no need to change anything
    }

    myContentPanel.setPreferredSize(new Dimension(myContentPanel.getPreferredSize().width, requiredHeight));
    myContentPanel.revalidate();
    myPopup.pack(false, true);
    myPopup.moveToFitScreen(); // the popup may expand out of screen, move it back if necessary
  }

  private @NotNull Rectangle calculateBounds() {
    JFrame frame = (JFrame)ComponentUtil.findUltimateParent(myProgressPanel.getComponent());

    Dimension contentSize = myContentPanel.getPreferredSize();
    int contentHeight = Math.max(contentSize.height, JBUI.scale(100));

    int titleHeight = 0;
    if (myPopup instanceof AbstractPopup) {
      titleHeight = ((AbstractPopup)myPopup).getHeaderPreferredSize().height;
    }

    Rectangle frameBounds = frame.getBounds();
    int fullHeight = frameBounds.height - titleHeight;

    boolean isEmpty = myIndicatorPanel.getComponentCount() == 0;
    int width = Math.clamp(contentSize.width, JBUI.scale(300), JBUI.scale(500));
    int height = Math.min(isEmpty ? frameBounds.height / 4 : fullHeight, contentHeight);

    int x = frameBounds.x + frameBounds.width - width - JBUI.scale(20);
    int y = frameBounds.y + frameBounds.height - height;

    if (height != fullHeight) {
      y -= JBUI.scale(10);
    }

    StatusBarEx sb = (StatusBarEx)((IdeFrame)frame).getStatusBar();
    if (sb != null && sb.isVisible()) {
      int statusBarHeight = sb.getSize().height;
      if (height == fullHeight) {
        height -= statusBarHeight + JBUI.scale(10);
      }
      else {
        y -= statusBarHeight;
      }
    }

    y -= titleHeight;

    return new Rectangle(x, y, width, height);
  }

  public void show(boolean requestFocus) {
    updateContentUI();

    createPopup(myContentPanel, myIndicatorPanel, requestFocus);

    ApplicationManager.getApplication().getMessageBus().connect(myPopup).subscribe(LafManagerListener.TOPIC, source -> updateContentUI());

    Rectangle popupBounds = calculateBounds();
    myContentPanel.setPreferredSize(popupBounds.getSize());
    myPopupVisible = true;
    myPopup.showInScreenCoordinates(myProgressPanel.getComponent().getRootPane(), popupBounds.getLocation());
    ensureSufficientSize();
  }

  public boolean isShowing() {
    return myPopupVisible;
  }

  public void hide() {
    if (myPopup != null) {
      myAnalyzingBannerDecorator.handlePopupClose();
      mySeparatorDecorator.handlePopupClose();

      myPopupVisible = false;
      myPopup.cancel();
      myPopup = null;
      myContentPanel.setPreferredSize(null);
    }
  }

  private void revalidateAll() {
    myContentPanel.doLayout();
    myContentPanel.revalidate();
    myContentPanel.repaint();
  }

  private void updateContentUI() {
    IJSwingUtilities.updateComponentTreeUI(myContentPanel);
    myContentPanel.getViewport().setBackground(myIndicatorPanel.getBackground());
    myContentPanel.setBorder(null);
  }

  public void setHideOnFocusLost(boolean value) {
    if (myPopup instanceof AbstractPopup popup) {
      popup.setCancelOnClickOutside(value);
      popup.setCancelOnOtherWindowOpen(value);
    }
  }

  static void hideSeparator(@NotNull Component component) {
    ProgressPanel panel = ClientProperty.get(component, KEY);
    if (panel != null) {
      panel.setSeparatorEnabled(false);
    }
  }

  static boolean isProgressIndicator(@NotNull Component component) {
    return ClientProperty.get(component, KEY) != null;
  }

  private void createPopup(@NotNull JComponent content, @NotNull JComponent focus, boolean requestFocus) {
    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(content, focus);
    builder.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        clearNativeAccessibleResourcesIfNeeded();
        myProgressPanel.hideProcessPopup();
      }
    });

    builder.setNormalWindowLevel(true);
    builder.setMovable(true);
    builder.setResizable(true);
    builder.setTitle(IdeBundle.message("progress.window.title"));
    builder.setCancelOnClickOutside(false);
    builder.setRequestFocus(requestFocus);
    builder.setBelongsToGlobalPopupStack(false);
    builder.setMinSize(POPUP_MIN_SIZE);
    Project project = ProjectUtil.getProjectForComponent(myProgressPanel.getComponent());
    builder.setDimensionServiceKey(project, DIMENSION_SERVICE_KEY, true);
    builder.setLocateWithinScreenBounds(false);
    
    if (StartupUiUtil.isWaylandToolkit()) {
      builder.setHeaderAlwaysFocusable(true);
    }

    builder.setCancelButton(new MinimizeButton(IdeBundle.message("tooltip.hide")));

    builder.setKeyboardActions(List.of(
      Pair.create(_ -> myProgressPanel.hideProcessPopup(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0))
    ));

    myPopup = builder.createPopup();
  }

  private void clearNativeAccessibleResourcesIfNeeded() {
    if (OS.CURRENT != OS.macOS || !ScreenReader.isActive()) {
      return;
    }
    // Content panel's components are reused, but the popup window is recreated.
    // On macOS, we have to clear the native accessible peers so that for the next popup show they are attached to the new window.
    // Otherwise, VoiceOver doesn't read the components when the popup opens after the first time.
    AWTAccessor.AccessibleContextAccessor accessor = AWTAccessor.getAccessibleContextAccessor();
    for (Component component : UIUtil.uiTraverser(myContentPanel)) {
      if (component instanceof Accessible) {
        AccessibleContext context = component.getAccessibleContext();
        if (context != null) {
          accessor.setNativeAXResource(context, null);
        }
      }
    }
  }

  private final class ProcessPopupFocusTraversalPolicy extends ComponentsListFocusTraversalPolicy {
    ProcessPopupFocusTraversalPolicy() {
      super(true);
    }

    @Override
    protected @NotNull List<Component> getOrderedComponents() {
      List<Component> result = new ArrayList<>();
      boolean screenReaderActive = ScreenReader.isActive();
      for (Component c : UIUtil.uiTraverser(myIndicatorPanel)) {
        if (!c.isVisible()) {
          continue;
        }
        if (c instanceof InplaceButton || c instanceof LinkLabel) {
          result.add(c);
        }
        else if (screenReaderActive && c instanceof JProgressBar) {
          result.add(c);
        }
      }
      return result;
    }

    @Override
    public Component getComponentAfter(Container container, Component component) {
      Component after = super.getComponentAfter(container, component);
      return after != null ? after : getFirstComponent(container);
    }

    @Override
    public Component getComponentBefore(Container container, Component component) {
      Component before = super.getComponentBefore(container, component);
      return before != null ? before : getLastComponent(container);
    }
  }

  private class MyJBPanelWithEmptyText extends JBPanelWithEmptyText {
    private MyJBPanelWithEmptyText() { super(new VerticalLayout(0)); }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleIndicatorPanel();
      }
      return accessibleContext;
    }

    private class AccessibleIndicatorPanel extends AccessibleJPanel {
      @Override
      public AccessibleRole getAccessibleRole() {
        return AccessibilityUtils.GROUPED_ELEMENTS;
      }

      @Override
      @SuppressWarnings("HardCodedStringLiteral")
      public String getAccessibleName() {
        String emptyText = getVisibleEmptyText();
        if (emptyText != null) {
          return emptyText;
        }

        List<String> progressTexts = new ArrayList<>();
        for (Component component : myIndicatorPanel.getComponents()) {
          if (!component.isVisible()) {
            continue;
          }

          ProgressPanel progressPanel = ClientProperty.get(component, KEY);
          if (progressPanel == null) {
            continue;
          }

          progressTexts.add(progressPanel.getLabelText());
          progressTexts.add(progressPanel.getCommentText());
        }

        String progressText = AccessibleContextUtil.joinAccessibleStrings(". ", progressTexts.toArray(String[]::new));
        return progressText != null ? progressText : super.getAccessibleName();
      }

      private @Nullable String getVisibleEmptyText() {
        for (Component component : myIndicatorPanel.getComponents()) {
          if (component.isVisible()) {
            return null;
          }
        }

        String emptyText = myIndicatorPanel.getEmptyText().getText();
        return StringUtil.isEmptyOrSpaces(emptyText) ? null : emptyText;
      }
    }
  }
}