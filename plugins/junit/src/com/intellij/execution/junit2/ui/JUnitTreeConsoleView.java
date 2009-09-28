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

import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.junit.JUnitProcessHandler;
import com.intellij.execution.junit2.InputRouter;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.TestingStatus;
import com.intellij.execution.junit2.TreeConsumer;
import com.intellij.execution.junit2.segments.*;
import com.intellij.execution.junit2.states.TestStateUpdater;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.model.TreeCollapser;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.ExternalOutput;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.testframework.ui.TestResultsPanel;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Disposer;

import javax.swing.*;

public class JUnitTreeConsoleView extends BaseTestsOutputConsoleView {
  private ConsolePanel myConsolePanel;
  private JUnitRunningModel myModel;
  private final JUnitConsoleProperties myProperties;
  private final RunnerSettings myRunnerSettings;
  private final ConfigurationPerRunnerSettings myConfigurationSettings;

  public JUnitTreeConsoleView(final JUnitConsoleProperties properties,
                              final RunnerSettings runnerSettings,
                              final ConfigurationPerRunnerSettings configurationSettings) {
    super(properties);
    myProperties = properties;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
    getPrinter().setCollectOutput(true);
  }

  protected TestResultsPanel createTestResultsPanel() {
    myConsolePanel = new ConsolePanel(getConsole().getComponent(), getPrinter(), myProperties, myRunnerSettings, myConfigurationSettings,
                                      getConsole().createConsoleActions());
    return myConsolePanel;
  }

  public void attachToProcess(final ProcessHandler processHandler) {
    final JUnitProcessHandler jUnitProcessHandler = (JUnitProcessHandler)processHandler;
    final PacketExtractorBase out = jUnitProcessHandler.getOut();
    final PacketExtractorBase err = jUnitProcessHandler.getErr();
    final DeferedActionsQueue queue = attachPacketExtractors(out, err);
    attachTo(out, err, processHandler, queue);
  }

  private static DeferedActionsQueue attachPacketExtractors(final PacketExtractorBase myOutPacketExtractor,
                                                           final PacketExtractorBase myErrPacketExtractor) {
    final DeferedActionsQueue fulfilledWorkGate = new DeferedActionsQueueImpl();
    myOutPacketExtractor.setFulfilledWorkGate(fulfilledWorkGate);
    myErrPacketExtractor.setFulfilledWorkGate(fulfilledWorkGate);
    return fulfilledWorkGate;
  }

  private void attachTo(final PacketExtractorBase outPacketExtractor, final PacketExtractorBase errPacketExtractor, final ProcessHandler process, final DeferedActionsQueue queue) {
    final TestingStatus testingStatus = new TestingStatus(queue);
    testingStatus.setInputConsumer(new SystemOutput(myConsolePanel.getPrinter()));
    myConsolePanel.onProcessStarted(process);
    final TestStateUpdater testStateUpdater = new TestStateUpdater();
    final TreeConsumer treeConsumer = createTreeConsumer(testingStatus, outPacketExtractor, testStateUpdater);
    connectOperators(process, outPacketExtractor, errPacketExtractor, testingStatus, treeConsumer, testStateUpdater);
  }

  private TreeConsumer createTreeConsumer(final TestingStatus testingStatus,
                                          final PacketExtractorBase outPacketExtractor,
                                          final TestStateUpdater testStateUpdater) {
    return new TreeConsumer() {
      protected void onTreeAvailable(final TestProxy test) {
        if (myModel != null) {
          Disposer.dispose(myModel);
        }
        if (myConsolePanel == null) return;
        getPrinter().setCollectOutput(false);
        testingStatus.setInputConsumer(test);
        myModel = new JUnitRunningModel(test, testingStatus, myProperties);
        Disposer.register(JUnitTreeConsoleView.this, myModel);
        myModel.attachTo(outPacketExtractor);
        myConsolePanel.attachToModel(myModel);
        myModel.attachToTree(myConsolePanel.getTreeView());
        myConsolePanel.setModel(myModel);
        testStateUpdater.setRoot(test);
        myModel.onUIBuilt();
        new TreeCollapser().setModel(myModel);
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
    myModel = null;
    myConsolePanel = null;
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

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myConsolePanel.getTreeView();
  }

  public JUnitRunningModel getModel() {
    return myModel;
  }

  private static class SystemOutput implements InputConsumer {
    private final Printer myPrinter;

    public SystemOutput(final Printer printer) {
      myPrinter = printer;
    }

    public void onOutput(final String text, final ConsoleViewContentType contentType) {
      myPrinter.onNewAvailable(new ExternalOutput(text, contentType));
    }
  }
}
