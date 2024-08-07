// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.testframework.ToolbarPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;


public abstract class TestResultsPanel extends JPanel implements Disposable, UiCompatibleDataProvider  {
  private JScrollPane myLeftPane;
  protected final JComponent myConsole;
  protected ToolbarPanel myToolbarPanel;
  private final String mySplitterProportionProperty;
  private final float mySplitterDefaultProportion;
  protected final AnAction[] myConsoleActions;
  protected final TestConsoleProperties myProperties;
  protected TestStatusLine myStatusLine;
  private JBSplitter mySplitter;
  private JComponent myToolbarComponent;

  protected TestResultsPanel(@NotNull JComponent console, AnAction[] consoleActions, TestConsoleProperties properties,
                             @NotNull String splitterProportionProperty, float splitterDefaultProportion) {
    super(new BorderLayout(0,1));
    myConsole = console;
    myConsoleActions = consoleActions;
    myProperties = properties;
    mySplitterProportionProperty = splitterProportionProperty;
    mySplitterDefaultProportion = splitterDefaultProportion;
    final ToolWindowManagerListener listener = new ToolWindowManagerListener() {
      @Override
      public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
        mySplitter.setOrientation(splitVertically());
        revalidate();
        repaint();
      }
    };
    properties.getProject().getMessageBus().connect(this).subscribe(ToolWindowManagerListener.TOPIC, listener);
  }

  @NotNull
  public TestStatusLine getStatusLine() {
    return myStatusLine;
  }

  protected void hideToolbar() {
    myLeftPane.setBorder(JBUI.Borders.empty());
  }

  public void initUI() {
    myLeftPane = ScrollPaneFactory.createScrollPane();
    myLeftPane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.TOP);
    myStatusLine = createStatusLine();
    JComponent testTreeView = createTestTreeView();
    myToolbarPanel = createToolbarPanel();
    Disposer.register(this, myToolbarPanel);
    boolean splitVertically = splitVertically();
    mySplitter = createSplitter(mySplitterProportionProperty,
                                mySplitterDefaultProportion,
                                splitVertically);
    if (mySplitter instanceof OnePixelSplitter) {
      ((OnePixelSplitter)mySplitter).setBlindZone(() -> JBUI.insetsTop(myToolbarPanel.getHeight()));
    }
    Disposer.register(this, new Disposable(){
      @Override
      public void dispose() {
        remove(mySplitter);
        mySplitter.dispose();
      }
    });
    mySplitter.setOpaque(false);
    add(mySplitter, BorderLayout.CENTER);
    final JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(myLeftPane, BorderLayout.CENTER);
    leftPanel.add(myToolbarPanel, BorderLayout.NORTH);
    mySplitter.setFirstComponent(leftPanel);
    myStatusLine.setMinimumSize(new Dimension(0, myStatusLine.getMinimumSize().height));
    final JPanel rightPanel = new NonOpaquePanel(new BorderLayout());
    rightPanel.add(SameHeightPanel.wrap(myStatusLine, myToolbarPanel), BorderLayout.NORTH);
    JPanel outputTab = new NonOpaquePanel(new BorderLayout());
    myConsole.setFocusable(true);
    final Color editorBackground = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
    myConsole.setBorder(new CompoundBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT), new SideBorder(editorBackground, SideBorder.LEFT)));
    outputTab.add(myConsole, BorderLayout.CENTER);
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("TestRunnerResults", new DefaultActionGroup(myConsoleActions), false);
    toolbar.setTargetComponent(myConsole instanceof ComponentContainer ? ((ComponentContainer)myConsole).getPreferredFocusableComponent() : myConsole);
    myToolbarComponent = toolbar.getComponent();
    outputTab.add(myToolbarComponent, BorderLayout.EAST);
    rightPanel.add(outputTab, BorderLayout.CENTER);
    mySplitter.setSecondComponent(rightPanel);
    if (!ExperimentalUI.isNewUI()) {
      testTreeView.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
    }
    setLeftComponent(testTreeView);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    myStatusLine.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, myToolbarComponent.getPreferredSize().width));
  }

  private boolean splitVertically() {
    final String windowId = myProperties.getExecutor().getToolWindowId();
    final ToolWindow toolWindow = ToolWindowManager.getInstance(myProperties.getProject()).getToolWindow(windowId);
    boolean splitVertically = false;
    if (toolWindow != null) {
      final ToolWindowAnchor anchor = toolWindow.getAnchor();
      splitVertically = anchor == ToolWindowAnchor.LEFT || anchor == ToolWindowAnchor.RIGHT;
    }
    return splitVertically;
  }

  @ApiStatus.Internal
  protected ToolbarPanel createToolbarPanel() {
    return new ToolbarPanel(myProperties, this);
  }

  protected TestStatusLine createStatusLine() {
    return new TestStatusLine();
  }

  protected abstract JComponent createTestTreeView();

  @Nullable
  protected TestTreeView getTreeView() {
    return null;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    DataSink.uiDataSnapshot(sink, getTreeView());
  }

  @Override
  public void dispose() {
  }

  @NotNull
  protected static JBSplitter createSplitter(@NotNull String proportionProperty, float defaultProportion, boolean splitVertically) {
    JBSplitter splitter = new OnePixelSplitter(splitVertically, proportionProperty, defaultProportion);
    splitter.setHonorComponentsMinimumSize(true);
    return splitter;
  }

  protected void setLeftComponent(final JComponent component) {
    if (component != myLeftPane.getViewport().getView()) myLeftPane.setViewportView(component);
  }
}
