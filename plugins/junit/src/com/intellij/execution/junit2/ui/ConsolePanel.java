/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.execution.junit2.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.actions.JUnitToolbarPanel;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.ui.TestResultsPanel;
import com.intellij.execution.testframework.ui.TestsOutputConsolePrinter;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.AwtVisitor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

class ConsolePanel extends TestResultsPanel implements Disposable {
  @NonNls private static final String PROPORTION_PROPERTY = "test_tree_console_proprtion";
  private static final float DEFAULT_PROPORTION = 0.2f;

  private StatusLine myStatusLine;
  private JScrollPane myLeftPane;
  private JUnitToolbarPanel myToolbarPanel;
  private StatisticsPanel myStatisticsPanel;
  private TestTreeView myTreeView;
  private final JComponent myConsole;
  private TestsOutputConsolePrinter myPrinter;
  private final JUnitConsoleProperties myProperties;
  private final RunnerSettings myRunnerSettings;
  private final ConfigurationPerRunnerSettings myConfigurationSettings;
  private final AnAction[] myConsoleActions;
  private StartingProgress myStartingProgress;
  private Splitter mySplitter;

  public ConsolePanel(final JComponent console,
                      final TestsOutputConsolePrinter printer,
                      final JUnitConsoleProperties properties,
                      final RunnerSettings runnerSettings,
                      final ConfigurationPerRunnerSettings configurationSettings, AnAction[] consoleActions) {
    myConsole = console;
    myPrinter = printer;
    myProperties = properties;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
    myConsoleActions = consoleActions;
  }

  public void initUI() {
    myLeftPane = ScrollPaneFactory.createScrollPane();
    myLeftPane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.TOP | SideBorder.RIGHT);
    myStatisticsPanel = new StatisticsPanel();
    myToolbarPanel = new JUnitToolbarPanel(myProperties, myRunnerSettings, myConfigurationSettings, this);
    myStatusLine = new StatusLine();
    myTreeView = new JUnitTestTreeView();
    final Splitter splitter = createSplitter(PROPORTION_PROPERTY, DEFAULT_PROPORTION);
    Disposer.register(this, new Disposable(){
      public void dispose() {
        remove(splitter);
        splitter.dispose();
      }
    });
    add(splitter);
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
        if (component instanceof JScrollPane) {
          ((JScrollPane) component).putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.TOP | SideBorder.LEFT);
          return true;
        }
        return false;
      }
    };
    mySplitter.setFirstComponent(createOutputTab(myConsole, myConsoleActions));
    if (TestConsoleProperties.SHOW_STATISTICS.value(myProperties)) {
      mySplitter.setSecondComponent(myStatisticsPanel);
    }
    myProperties.addListener(TestConsoleProperties.SHOW_STATISTICS, new TestFrameworkPropertyListener<Boolean>() {
      public void onChanged(Boolean value) {
        if (value.booleanValue()) {
          mySplitter.setSecondComponent(myStatisticsPanel);
        }
        else {
          mySplitter.setSecondComponent(null);
        }
      }
    });
    rightPanel.add(mySplitter, BorderLayout.CENTER);
    splitter.setSecondComponent(rightPanel);
    myStartingProgress = new StartingProgress(myTreeView);
    setLeftComponent(myTreeView);
  }

  private static JComponent createOutputTab(JComponent console, AnAction[] consoleActions) {
    JPanel outputTab = new JPanel(new BorderLayout());
    outputTab.add(console, BorderLayout.CENTER);
    final DefaultActionGroup actionGroup = new DefaultActionGroup(consoleActions);
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, false);
    outputTab.add(toolbar.getComponent(), BorderLayout.WEST);
    return outputTab;
  }

  public void onProcessStarted(final ProcessHandler process) {
    myStatusLine.onProcessStarted(process);
    if (myStartingProgress == null) return;
    myStartingProgress.start(process);
  }

  public void setModel(final JUnitRunningModel model) {
    stopStartingProgress();
    final TestTreeView treeView = model.getTreeView();
    treeView.setLargeModel(true);
    setLeftComponent(treeView);
    myToolbarPanel.setModel(model);
    myStatusLine.setModel(model);

    model.addListener(new JUnitAdapter() {
      @Override
      public void onTestSelected(final TestProxy test) {
        if (myPrinter != null) myPrinter.updateOnTestSelected(test);
      }
    });
    myStatisticsPanel.attachTo(model);
  }

  private void stopStartingProgress() {
    if (myStartingProgress != null) myStartingProgress.doStop();
    myStartingProgress = null;
  }

  public TestTreeView getTreeView() {
    return myTreeView;
  }

  public Printer getPrinter() {
    return myPrinter;
  }

  private void setLeftComponent(final JComponent component) {
    if (component != myLeftPane.getViewport().getView()) myLeftPane.setViewportView(component);
  }

  private static Splitter createSplitter(final String proportionProperty, final float defaultProportion) {
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

  public void dispose() {
    stopStartingProgress();
    myPrinter = null;
  }

  public void attachToModel(final JUnitRunningModel model) {
    getTreeView().attachToModel(model);
  }

  private static class StartingProgress implements Runnable {
    private final Alarm myAlarm = new Alarm();
    private final Tree myTree;
    private final DefaultTreeModel myModel;
    private final DefaultMutableTreeNode myRootNode = new DefaultMutableTreeNode();
    private boolean myStarted = false;
    private boolean myStopped = false;
    private final SimpleColoredComponent myStartingLabel;
    private ProcessHandler myProcess;
    private long myStartedAt = System.currentTimeMillis();
    private final ProcessAdapter myProcessListener = new ProcessAdapter() {
      public void processTerminated(ProcessEvent event) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            doStop();
          }
        });
      }
    };

    public StartingProgress(final Tree tree) {
      myTree = tree;
      myModel = new DefaultTreeModel(myRootNode);
      myTree.setModel(myModel);
      myStartingLabel = new SimpleColoredComponent();

      //myStartingLabel.setBackground(UIManager.getColor("Tree.background"));
      myTree.setCellRenderer(new TreeCellRenderer() {
        public Component getTreeCellRendererComponent(final JTree tree, final Object value,
                                                      final boolean selected, final boolean expanded,
                                                      final boolean leaf, final int row, final boolean hasFocus) {
          myStartingLabel.clear();
          myStartingLabel.setIcon(PoolOfTestIcons.LOADING_ICON);
          myStartingLabel.append(getProgressText(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          if (!myStarted) postRepaint();
          return myStartingLabel;
        }
      });
      myTree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY, new PropertyChangeListener() {
        public void propertyChange(final PropertyChangeEvent evt) {
          myTree.removePropertyChangeListener(JTree.TREE_MODEL_PROPERTY, this);
          doStop();
        }
      });
    }

    private void doStop() {
      myStopped = true;
      myModel.nodeChanged(myRootNode);
      myAlarm.cancelAllRequests();
      if (myProcess != null) myProcess.removeProcessListener(myProcessListener);
      myProcess = null;
    }

    public void run() {
      myModel.nodeChanged(myRootNode);
      postRepaint();
    }

    private void postRepaint() {
      if (myStopped) return;
      myStarted = true;
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(this, 300, ModalityState.NON_MODAL);
    }

    public void start(final ProcessHandler process) {
      if (process.isProcessTerminated()) return;
      myProcess = process;
      myStartedAt = System.currentTimeMillis();
      process.addProcessListener(myProcessListener);
    }

    private String getProgressText() {
      if (myStopped) return ExecutionBundle.message("test.not.started.progress.text");
      final long millis = System.currentTimeMillis() - myStartedAt;
      final String phaseName = myProcess == null ? ExecutionBundle.message("starting.jvm.progress.text") : ExecutionBundle.message("instantiating.tests.progress.text");
      return phaseName + Formatters.printMinSec(millis);
    }
  }
}
