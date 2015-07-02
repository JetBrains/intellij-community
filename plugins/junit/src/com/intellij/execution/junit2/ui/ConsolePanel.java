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

package com.intellij.execution.junit2.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.actions.JUnitToolbarPanel;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.PoolOfTestIcons;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.testframework.ToolbarPanel;
import com.intellij.execution.testframework.ui.TestResultsPanel;
import com.intellij.execution.testframework.ui.TestStatusLine;
import com.intellij.execution.testframework.ui.TestsOutputConsolePrinter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ConsolePanel extends TestResultsPanel {
  @NonNls private static final String PROPORTION_PROPERTY = "test_tree_console_proprtion";
  private static final float DEFAULT_PROPORTION = 0.2f;

  private JUnitStatusLine myStatusLine;
  private StatisticsPanel myStatisticsPanel;
  private TestTreeView myTreeView;
  private TestsOutputConsolePrinter myPrinter;
  private StartingProgress myStartingProgress;

  public ConsolePanel(final JComponent console,
                      final TestsOutputConsolePrinter printer,
                      final JUnitConsoleProperties properties,
                      AnAction[] consoleActions) {
    super(console, consoleActions, properties, PROPORTION_PROPERTY, DEFAULT_PROPORTION);
    myPrinter = printer;
  }

  @Override
  public void initUI() {
    super.initUI();
    myStartingProgress = new StartingProgress(myTreeView);
  }

  @Override
  protected JComponent createStatisticsPanel() {
    myStatisticsPanel = new StatisticsPanel();
    return myStatisticsPanel;
  }

  @Override
  protected ToolbarPanel createToolbarPanel() {
    return new JUnitToolbarPanel(myProperties, this);
  }

  @Override
  protected TestStatusLine createStatusLine() {
    myStatusLine = new JUnitStatusLine();
    return myStatusLine;
  }

  @Override
  protected JComponent createTestTreeView() {
    myTreeView = new JUnitTestTreeView();
    return myTreeView;
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

  @Override
  public void dispose() {
    stopStartingProgress();
    myPrinter = null;
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
      @Override
      public void processTerminated(ProcessEvent event) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
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
      myTree.setPaintBusy(true);
      //myStartingLabel.setBackground(UIManager.getColor("Tree.background"));
      myTree.setCellRenderer(new TreeCellRenderer() {
        @NotNull
        @Override
        public Component getTreeCellRendererComponent(@NotNull final JTree tree, final Object value,
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
        @Override
        public void propertyChange(@NotNull final PropertyChangeEvent evt) {
          myTree.removePropertyChangeListener(JTree.TREE_MODEL_PROPERTY, this);
          doStop();
        }
      });
    }

    private void doStop() {
      myStopped = true;
      myTree.setPaintBusy(false);
      myModel.nodeChanged(myRootNode);
      myAlarm.cancelAllRequests();
      if (myProcess != null) myProcess.removeProcessListener(myProcessListener);
      myProcess = null;
    }

    @Override
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
