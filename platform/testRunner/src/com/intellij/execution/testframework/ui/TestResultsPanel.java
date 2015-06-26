/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.execution.testframework.TestFrameworkPropertyListener;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.testframework.ToolbarPanel;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author yole
 */
public abstract class TestResultsPanel extends JPanel implements Disposable, DataProvider  {
  private JScrollPane myLeftPane;
  private JComponent myStatisticsComponent;
  private Splitter myStatisticsSplitter;
  protected final JComponent myConsole;
  protected ToolbarPanel myToolbarPanel;
  private final String mySplitterProportionProperty;
  private final String myStatisticsSplitterProportionProperty;
  private final float mySplitterDefaultProportion;
  protected final AnAction[] myConsoleActions;
  protected final TestConsoleProperties myProperties;
  protected TestStatusLine myStatusLine;
  private Splitter mySplitter;

  protected TestResultsPanel(@NotNull JComponent console, AnAction[] consoleActions, TestConsoleProperties properties,
                             String splitterProportionProperty, float splitterDefaultProportion) {
    super(new BorderLayout(0,1));
    myConsole = console;
    myConsoleActions = consoleActions;
    myProperties = properties;
    mySplitterProportionProperty = splitterProportionProperty;
    mySplitterDefaultProportion = splitterDefaultProportion;
    myStatisticsSplitterProportionProperty = mySplitterProportionProperty + "_Statistics";
  }

  public void initUI() {
    myLeftPane = ScrollPaneFactory.createScrollPane();
    myLeftPane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.TOP);
    myStatisticsComponent = createStatisticsPanel();
    myStatusLine = createStatusLine();
    JComponent testTreeView = createTestTreeView();
    myToolbarPanel = createToolbarPanel();
    Disposer.register(this, myToolbarPanel);
    final String windowId = myProperties.getExecutor().getToolWindowId();
    final ToolWindow toolWindow = ToolWindowManager.getInstance(myProperties.getProject()).getToolWindow(windowId);
    boolean splitVertically = false;
    if (toolWindow != null) {
      final ToolWindowAnchor anchor = toolWindow.getAnchor();
      splitVertically = anchor == ToolWindowAnchor.LEFT || anchor == ToolWindowAnchor.RIGHT;
    }
    myStatusLine.setPreferredSize(splitVertically);
    
    mySplitter = createSplitter(mySplitterProportionProperty,
                                mySplitterDefaultProportion,
                                splitVertically);
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
    myStatisticsSplitter = createSplitter(myStatisticsSplitterProportionProperty, 0.5f, false);
    myStatisticsSplitter.setFirstComponent(createOutputTab(myConsole, myConsoleActions));
    if (Registry.is("tests.view.old.statistics.panel")) {
      if (TestConsoleProperties.SHOW_STATISTICS.value(myProperties)) {
        showStatistics();
      }
      myProperties.addListener(TestConsoleProperties.SHOW_STATISTICS, new TestFrameworkPropertyListener<Boolean>() {
        @Override
        public void onChanged(Boolean value) {
          if (value.booleanValue()) {
            showStatistics();
          }
          else {
            myStatisticsSplitter.setSecondComponent(null);
          }
        }
      });
    }
    rightPanel.add(myStatisticsSplitter, BorderLayout.CENTER);
    mySplitter.setSecondComponent(rightPanel);
    testTreeView.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
    setLeftComponent(testTreeView);
  }

  private void showStatistics() {
    myStatisticsSplitter.setSecondComponent(myStatisticsComponent);
  }

  protected abstract JComponent createStatisticsPanel();

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

  private JComponent createOutputTab(JComponent console,
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

  protected static Splitter createSplitter(final String proportionProperty, final float defaultProportion, boolean splitVertically) {
    final Splitter splitter = new OnePixelSplitter(splitVertically);
    splitter.setHonorComponentsMinimumSize(true);
    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    float proportion;
    final String value = propertiesComponent.getValue(proportionProperty);
    if (value != null) {
      try {
        proportion = Float.parseFloat(value);
      }
      catch (NumberFormatException e) {
        proportion = defaultProportion;
      }
    }
    else {
      proportion = defaultProportion;
    }

    splitter.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(@NotNull final PropertyChangeEvent event) {
        if (event.getPropertyName().equals(Splitter.PROP_PROPORTION)) {
          propertiesComponent.setValue(proportionProperty, String.valueOf(splitter.getProportion()));
        }
      }
    });
    splitter.setProportion(proportion);
    return splitter;
  }

  protected void setLeftComponent(final JComponent component) {
    if (component != myLeftPane.getViewport().getView()) myLeftPane.setViewportView(component);
  }
}
