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
package com.intellij.execution.testframework.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.testframework.ToolbarPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.*;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;

/**
 * @author yole
 */
public abstract class TestResultsPanel extends JPanel implements Disposable, DataProvider  {
  private JScrollPane myLeftPane;
  protected final JComponent myConsole;
  protected ToolbarPanel myToolbarPanel;
  private final String mySplitterProportionProperty;
  private final float mySplitterDefaultProportion;
  protected final AnAction[] myConsoleActions;
  protected final TestConsoleProperties myProperties;
  protected TestStatusLine myStatusLine;
  private JBSplitter mySplitter;

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
      public void toolWindowRegistered(@NotNull String id) {
      }

      @Override
      public void stateChanged() {
        final boolean splitVertically = splitVertically();
        myStatusLine.setPreferredSize(splitVertically);
        mySplitter.setOrientation(splitVertically);
        revalidate();
        repaint();
      }
    };
    ToolWindowManagerEx.getInstanceEx(properties.getProject()).addToolWindowManagerListener(listener, this);
  }

  public void initUI() {
    myLeftPane = ScrollPaneFactory.createScrollPane();
    myLeftPane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.TOP);
    myStatusLine = createStatusLine();
    JComponent testTreeView = createTestTreeView();
    myToolbarPanel = createToolbarPanel();
    Disposer.register(this, myToolbarPanel);
    boolean splitVertically = splitVertically();
    myStatusLine.setPreferredSize(splitVertically);
    
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
    add(mySplitter, BorderLayout.CENTER);
    final JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(myLeftPane, BorderLayout.CENTER);
    leftPanel.add(myToolbarPanel, BorderLayout.NORTH);
    mySplitter.setFirstComponent(leftPanel);
    myStatusLine.setMinimumSize(new Dimension(0, myStatusLine.getMinimumSize().height));
    myStatusLine.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
    final JPanel rightPanel = new JPanel(new BorderLayout());
    rightPanel.add(SameHeightPanel.wrap(myStatusLine, myToolbarPanel), BorderLayout.NORTH);
    rightPanel.add(createOutputTab(myConsole, myConsoleActions), BorderLayout.CENTER);
    mySplitter.setSecondComponent(rightPanel);
    testTreeView.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
    setLeftComponent(testTreeView);
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

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    final TestTreeView view = getTreeView();
    if (view != null) {
      return view.getData(dataId);
    }
    return null;
  }

  private static JComponent createOutputTab(JComponent console,
                                            AnAction[] consoleActions) {
    JPanel outputTab = new JPanel(new BorderLayout());
    console.setFocusable(true);
    final Color editorBackground = EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
    console.setBorder(new CompoundBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT | SideBorder.TOP),
                                         new SideBorder(editorBackground, SideBorder.LEFT)));
    outputTab.add(console, BorderLayout.CENTER);
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, new DefaultActionGroup(consoleActions), false);
    outputTab.add(toolbar.getComponent(), BorderLayout.EAST);
    return outputTab;
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
