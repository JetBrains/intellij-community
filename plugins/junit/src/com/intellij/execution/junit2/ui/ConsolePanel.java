package com.intellij.execution.junit2.ui;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.diagnostic.logging.LogConsole;
import com.intellij.diagnostic.logging.LogConsoleManager;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit2.Printer;
import com.intellij.execution.junit2.ui.actions.JUnitToolbarPanel;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.Alarm;
import com.intellij.util.ui.Tree;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

class ConsolePanel extends JPanel implements LogConsoleManager, Disposable {
  @NonNls private static final String PROPORTION_PROPERTY = "test_tree_console_proprtion";
  private static final float DEFAULT_PROPORTION = 0.2f;

  private static final Icon OUTPUT_TAB_ICON = TestsUIUtil.loadIcon("testOuput");
  private static final Icon STATISTICS_TAB_ICON = TestsUIUtil.loadIcon("testStatistics");

  private final StatusLine myStatusLine;
  private final JScrollPane myLeftPane;
  private final JUnitToolbarPanel myToolbarPanel;
  private final StatisticsPanel myStatisticsPanel;
  private final TestTreeView myTreeView;
  private ConsoleViewPrinter myPrinter;
  private StartingProgress myStartingProgress;
  private TabbedPaneWrapper myTabs;

  private final LogFilesManager myLogFilesManager;
  private ProcessHandler myRunProcess = null;
  private JUnitConfiguration myConfiguration;
  private Map<AdditionalTabComponent, Integer> myAdditionalComponents = new HashMap<AdditionalTabComponent, Integer>();

  public ConsolePanel(final JComponent console,
                      final ConsoleViewPrinter printer,
                      final JUnitConsoleProperties properties,
                      final RunnerSettings runnerSettings,
                      final ConfigurationPerRunnerSettings configurationSettings) {
    super(new BorderLayout(0,1));
    myConfiguration = properties.getConfiguration();
    myLogFilesManager = new LogFilesManager(properties.getProject(), this);
    myPrinter = printer;
    myLeftPane = ScrollPaneFactory.createScrollPane();
    myStatisticsPanel = new StatisticsPanel();
    myToolbarPanel = new JUnitToolbarPanel(properties, runnerSettings, configurationSettings);
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
    myTabs = new TabbedPaneWrapper(SwingConstants.TOP);
    myTabs.addTab(ExecutionBundle.message("output.tab.title"), OUTPUT_TAB_ICON, console, null);
    myTabs.addTab(ExecutionBundle.message("statistics.tab.title"), STATISTICS_TAB_ICON, myStatisticsPanel, null);
    initAdditionalTabs();
    rightPanel.add(myTabs.getComponent(), BorderLayout.CENTER);
    splitter.setSecondComponent(rightPanel);
    myStartingProgress = new StartingProgress(myTreeView);
    setLeftComponent(myTreeView);
  }

  public void onProcessStarted(final ProcessHandler process) {
    myStatusLine.onProcessStarted(process);
    if (myStartingProgress == null) return;
    myStartingProgress.start(process);
  }

  private void initAdditionalTabs(){
    myLogFilesManager.registerFileMatcher(myConfiguration);
    myLogFilesManager.initLogConsoles(myConfiguration, myRunProcess);
  }

  public void addLogConsole(final String name, final String path, final long skippedContent){
    final LogConsole log = new LogConsole(myConfiguration.getProject(), new File(path), skippedContent, name) {
      public boolean isActive() {
        return myTabs.getSelectedComponent() == this;
      }
    };

    if (myRunProcess != null) {
      log.attachStopLogConsoleTrackingListener(myRunProcess);
    }
    addAdditionalTabComponent(log);
    myTabs.addChangeListener(log);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        myTabs.removeChangeListener(log);
      }
    });
  }

  public void removeLogConsole(final String path) {
    LogConsole componentToRemove = null;
    for (AdditionalTabComponent tabComponent : myAdditionalComponents.keySet()) {
      if (tabComponent instanceof LogConsole) {
        final LogConsole console = (LogConsole)tabComponent;
        if (Comparing.strEqual(console.getPath(), path)) {
          componentToRemove = console;
          break;
        }
      }
    }
    if (componentToRemove != null) {
      myTabs.removeChangeListener(componentToRemove);
      removeAdditionalTabComponent(componentToRemove);
    }
  }

  public void attachStopLogConsoleTrackingListeners(ProcessHandler process) {
    myRunProcess = process;
    for (AdditionalTabComponent component: myAdditionalComponents.keySet()) {
      if (component instanceof LogConsole){
        ((LogConsole)component).attachStopLogConsoleTrackingListener(process);
      }
    }
  }

  public void setModel(final JUnitRunningModel model) {
    stopStartingProgress();
    final TestTreeView treeView = model.getTreeView();
    treeView.setLargeModel(true);
    setLeftComponent(treeView);
    myToolbarPanel.setModel(model);
    myStatusLine.setModel(model);
    model.addListener(myPrinter);
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
    myLogFilesManager.unregisterFileMatcher();
    myPrinter = null;
  }

  public void attachToModel(final JUnitRunningModel model) {
    getTreeView().attachToModel(model);
  }

  public void addAdditionalTabComponent(final AdditionalTabComponent tabComponent) {
    myAdditionalComponents.put(tabComponent, myTabs.getTabCount());
    myTabs.addTab(tabComponent.getTabTitle(), null, tabComponent.getComponent(), tabComponent.getTooltip());
    Disposer.register(this, new Disposable() {
      public void dispose() {
        removeAdditionalTabComponent(tabComponent);
      }
    });
  }

  public void removeAdditionalTabComponent(AdditionalTabComponent component) {
    myTabs.removeTabAt(myAdditionalComponents.get(component).intValue());
    myAdditionalComponents.remove(component);
    component.dispose();
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
