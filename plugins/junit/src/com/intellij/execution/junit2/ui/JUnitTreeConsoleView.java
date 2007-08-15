package com.intellij.execution.junit2.ui;

import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.junit2.*;
import com.intellij.execution.junit2.segments.*;
import com.intellij.execution.junit2.states.TestStateUpdater;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.model.TreeCollapser;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ProfilingUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class JUnitTreeConsoleView extends WrappingConsoleView {
  private ConsolePanel myConsolePanel;
  private JUnitRunningModel myModel;
  private final JUnitConsoleProperties myProperties;

  public JUnitTreeConsoleView(final JUnitConsoleProperties properties,
                              final RunnerSettings runnerSettings,
                              final ConfigurationPerRunnerSettings configurationSettings) {
    super(properties);
    myProperties = properties;
    myProperties.setConsole(this);
    getPrinter().setCollectOutput(true);
    myConsolePanel = new ConsolePanel(getConsole().getComponent(), getPrinter(), myProperties, runnerSettings, configurationSettings);
  }

  protected void attachTo(final PacketExtractorBase outPacketExtractor, final PacketExtractorBase errPacketExtractor, final ProcessHandler process, final DeferedActionsQueue queue) {
    ProfilingUtil.operationStarted("junitInit");
    final TestingStatus testingStatus = new TestingStatus(queue);
    testingStatus.setInputConsumer(new SystemOutput(myConsolePanel.getPrinter()));
    myConsolePanel.onProcessStarted(process);
    myConsolePanel.attachStopLogConsoleTrackingListeners(process);
    final TestStateUpdater testStateUpdater = new TestStateUpdater();
    final TreeConsumer treeConsumer = createTreeConsumer(testingStatus, outPacketExtractor, testStateUpdater);
    connectOperators(process, outPacketExtractor, errPacketExtractor, testingStatus, treeConsumer, testStateUpdater);
  }

  private TreeConsumer createTreeConsumer(final TestingStatus testingStatus,
                                          final PacketExtractorBase outPacketExtractor,
                                          final TestStateUpdater testStateUpdater) {
    return new TreeConsumer() {
      protected void onTreeAvailable(final TestProxy test) {
        disposeModel();
        if (myConsolePanel == null)
          return;
        getPrinter().setCollectOutput(false);
        testingStatus.setInputConsumer(test);
        myModel = new JUnitRunningModel(test, testingStatus, myProperties);
        myModel.attachTo(outPacketExtractor);
        myConsolePanel.attachToModel(myModel);
        myModel.attachToTree(myConsolePanel.getTreeView());
        myConsolePanel.setModel(myModel);
        testStateUpdater.setRoot(test);
        myModel.onUIBuilt();
        new TreeCollapser().setModel(myModel);
        ProfilingUtil.operationFinished("junitInit");
      }
    };
  }


  public static void connectOperators(final ProcessHandler process, final PacketExtractorBase outPacketExtractor,
                                      final PacketExtractorBase errPacketExtractor, final TestingStatus testingStatus,
                                      final TreeConsumer treeConsumer, final TestStateUpdater testStateUpdater) {
    testingStatus.attachTo(process);

    final InputObjectRegistry registry = new InputObjectRegistryImpl();
    final PacketsDispatcher packetsDispatcher = installDispatcher(registry, outPacketExtractor, process, InputRouter.createOutRouter(), testingStatus);
    testingStatus.setPacketsDispatcher(packetsDispatcher);
    installDispatcher(registry, errPacketExtractor, process, InputRouter.createErrRouter(), testingStatus);
    packetsDispatcher.addListener(treeConsumer);
    packetsDispatcher.addListener(testStateUpdater);
    packetsDispatcher.addListener(testingStatus);
  }

  public void dispose() {
    super.dispose();
    disposeModel();
    if (myConsolePanel != null){
      Disposer.dispose(myConsolePanel);
      myConsolePanel = null;
    }
  }

  private void disposeModel() {
    if (myModel != null) {
      myModel.dispose();
      myModel = null;
    }
  }

  private static PacketsDispatcher installDispatcher(final InputObjectRegistry registry,
                                                     final PacketExtractorBase packetExtractor,
                                                     final ProcessHandler process, final InputRouter inputRouter, final InputConsumer defaultConsumer) {
    inputRouter.setInputConsumer(defaultConsumer);
    final PacketsDispatcher packetsDispatcher = new PacketsDispatcher(registry);
    packetExtractor.setPacketProcessor(packetsDispatcher);
    packetsDispatcher.addListener(inputRouter);
    inputRouter.attachTo(process);
    return packetsDispatcher;
  }

  public JComponent getComponent() {
    return myConsolePanel;
  }

  private static class SystemOutput implements InputConsumer {
    private final Printer myPrinter;

    public SystemOutput(final Printer printer) {
      myPrinter = printer;
    }

    public void onOutput(final String text, final ConsoleViewContentType contentType) {
      myPrinter.onNewAvaliable(new ExternalOutput(text, contentType));
    }
  }

  @NotNull
  public AnAction[] createUpDownStacktraceActions() {
    return getConsole().createUpDownStacktraceActions();
  }
}
