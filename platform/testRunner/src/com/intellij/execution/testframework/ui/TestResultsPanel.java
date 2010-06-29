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

import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkPropertyListener;
import com.intellij.execution.testframework.ToolbarPanel;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.AwtVisitor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author yole
 */
public abstract class TestResultsPanel extends JPanel implements Disposable {
  private JBScrollPane myLeftPane;
  private JComponent myStatisticsComponent;
  private Splitter mySplitter;
  protected final JComponent myConsole;
  protected ToolbarPanel myToolbarPanel;
  private final String mySplitterProportionProperty;
  private final float mySplitterDefaultProportion;
  protected final RunnerSettings myRunnerSettings;
  protected final ConfigurationPerRunnerSettings myConfigurationSettings;
  protected final AnAction[] myConsoleActions;
  protected final TestConsoleProperties myProperties;
  protected TestStatusLine myStatusLine;

  protected TestResultsPanel(@NotNull JComponent console, AnAction[] consoleActions, TestConsoleProperties properties,
                             RunnerSettings runnerSettings, ConfigurationPerRunnerSettings configurationSettings,
                             String splitterProportionProperty, float splitterDefaultProportion) {
    super(new BorderLayout(0,1));
    myConsole = console;
    myConsoleActions = consoleActions;
    myProperties = properties;
    mySplitterProportionProperty = splitterProportionProperty;
    mySplitterDefaultProportion = splitterDefaultProportion;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
  }

  public void initUI() {
    myLeftPane = ScrollPaneFactory.createScrollPane();
    myLeftPane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.TOP | SideBorder.RIGHT);
    myStatisticsComponent = createStatisticsPanel();
    myStatusLine = createStatusLine();
    JComponent testTreeView = createTestTreeView();
    myToolbarPanel = createToolbarPanel();
    Disposer.register(this, myToolbarPanel);
    final Splitter splitter = createSplitter(mySplitterProportionProperty, mySplitterDefaultProportion);
    Disposer.register(this, new Disposable(){
      public void dispose() {
        remove(splitter);
        splitter.dispose();
      }
    });
    add(splitter, BorderLayout.CENTER);
    final JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(myLeftPane, BorderLayout.CENTER);
    leftPanel.add(myToolbarPanel, BorderLayout.NORTH);
    splitter.setFirstComponent(leftPanel);
    myStatusLine.setMinimumSize(new Dimension(0, myStatusLine.getMinimumSize().height));
    final JPanel rightPanel = new JPanel(new BorderLayout());
    rightPanel.add(SameHeightPanel.wrap(myStatusLine, myToolbarPanel), BorderLayout.NORTH);
    mySplitter = new Splitter();
    new AwtVisitor(myConsole) {
      public boolean visit(Component component) {
        if (component instanceof JBScrollPane) {
          ((JBScrollPane) component).putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.TOP | SideBorder.LEFT);
          return true;
        }
        return false;
      }
    };
    mySplitter.setFirstComponent(createOutputTab(myConsole, myConsoleActions));
    final JBScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myStatisticsComponent);
    if (TestConsoleProperties.SHOW_STATISTICS.value(myProperties)) {
      mySplitter.setSecondComponent(scrollPane);
    }
    myProperties.addListener(TestConsoleProperties.SHOW_STATISTICS, new TestFrameworkPropertyListener<Boolean>() {
      public void onChanged(Boolean value) {
        if (value.booleanValue()) {
          mySplitter.setSecondComponent(scrollPane);
        }
        else {
          mySplitter.setSecondComponent(null);
        }
      }
    });
    rightPanel.add(mySplitter, BorderLayout.CENTER);
    splitter.setSecondComponent(rightPanel);
    setLeftComponent(testTreeView);
  }

  protected abstract JComponent createStatisticsPanel();

  protected ToolbarPanel createToolbarPanel() {
    return new ToolbarPanel(myProperties, myRunnerSettings, myConfigurationSettings, this);
  }

  protected TestStatusLine createStatusLine() {
    return new TestStatusLine();
  }

  protected abstract JComponent createTestTreeView();

  private static JComponent createOutputTab(JComponent console, AnAction[] consoleActions) {
    JPanel outputTab = new JPanel(new BorderLayout());
    outputTab.add(console, BorderLayout.CENTER);
    final DefaultActionGroup actionGroup = new DefaultActionGroup(consoleActions);
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, false);
    outputTab.add(toolbar.getComponent(), BorderLayout.WEST);
    return outputTab;
  }

  public void dispose() {
  }

  protected static Splitter createSplitter(final String proportionProperty, final float defaultProportion) {
    final Splitter splitter = new Splitter(false);
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
      public void propertyChange(final PropertyChangeEvent evt) {
        if (propertiesComponent == null) return;
        if (evt.getPropertyName().equals(Splitter.PROP_PROPORTION)) {
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
