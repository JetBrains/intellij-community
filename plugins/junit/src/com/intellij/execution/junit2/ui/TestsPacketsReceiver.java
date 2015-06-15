/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 11-Dec-2009
 */
package com.intellij.execution.junit2.ui;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.info.ClassBasedInfo;
import com.intellij.execution.junit2.info.DisplayTestInfoExtractor;
import com.intellij.execution.junit2.segments.InputObjectRegistry;
import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.execution.junit2.segments.OutputPacketProcessor;
import com.intellij.execution.junit2.states.*;
import com.intellij.execution.junit2.ui.model.CompletionEvent;
import com.intellij.execution.junit2.ui.model.JUnitListenersNotifier;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Printable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.execution.junit.segments.PoolOfDelimiters;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;
import com.intellij.util.containers.HashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestsPacketsReceiver implements OutputPacketProcessor, Disposable {
  private static final TIntObjectHashMap<StateChanger> STATE_CLASSES = new TIntObjectHashMap<StateChanger>();
  private Map<String, TestProxy> myKnownDynamicParents;
  private final TestProxy myUnboundOutput;

  static {
    mapClass(PoolOfTestStates.RUNNING_INDEX, new RunningStateSetter());
    mapClass(PoolOfTestStates.COMPLETE_INDEX, new TestCompleter());
    mapClass(PoolOfTestStates.FAILED_INDEX, new StateReader(FaultyState.class));
    mapClass(PoolOfTestStates.ERROR_INDEX, new StateReader(FaultyState.class));
    mapClass(PoolOfTestStates.IGNORED_INDEX, new StateReader(IgnoredState.class));
    mapClass(PoolOfTestStates.SKIPPED_INDEX, new StateReader(SkippedState.class));
    mapClass(PoolOfTestStates.COMPARISON_FAILURE, new StateReader(ComparisonFailureState.class));
  }

  public static void mapClass(final int magnitude, @NotNull StateChanger factory) {
    factory.setMagnitude(magnitude);
    STATE_CLASSES.put(magnitude, factory);
  }

  private final InputObjectRegistry myObjectRegistry;

  private final Set<TestProxy> myCurrentTests = new HashSet<TestProxy>();
  private boolean myIsTerminated = false;
  private JUnitRunningModel myModel;
  private final JUnitConsoleProperties myConsoleProperties;


  public TestsPacketsReceiver(@NotNull JUnitTreeConsoleView consoleView, @NotNull TestProxy unboundOutput) {
    myUnboundOutput = unboundOutput;
    myObjectRegistry = new InputObjectRegistry();
    myConsoleProperties = (JUnitConsoleProperties)consoleView.getProperties();
  }

  @Override
  public void processPacket(final String packet) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (packet.startsWith(PoolOfDelimiters.TREE_PREFIX)) {
      notifyStart(readNode(new ObjectReader(packet, PoolOfDelimiters.TREE_PREFIX.length(), myObjectRegistry)));
    }

    else if (packet.startsWith(PoolOfDelimiters.INPUT_COSUMER)) {
      notifyTestStart(new ObjectReader(packet, PoolOfDelimiters.INPUT_COSUMER.length(), myObjectRegistry));
    }

    else if (packet.startsWith(PoolOfDelimiters.CHANGE_STATE)) {
      notifyTestResult(new ObjectReader(packet, PoolOfDelimiters.CHANGE_STATE.length(), myObjectRegistry));
    }

    else if (packet.startsWith(PoolOfDelimiters.TESTS_DONE)) {
      notifyFinish(new ObjectReader(packet, PoolOfDelimiters.TESTS_DONE.length(), myObjectRegistry));
    }

    else if (packet.startsWith(PoolOfDelimiters.OBJECT_PREFIX)) {
      myObjectRegistry.readPacketFrom(new ObjectReader(packet, PoolOfDelimiters.OBJECT_PREFIX.length(), myObjectRegistry));
    }
  }

  @Override
  public void processOutput(Printable printable) {
    synchronized (myCurrentTests) {
      if (myCurrentTests.isEmpty()) {
        myUnboundOutput.addLast(printable);
      } else {
        for (TestProxy currentTest : myCurrentTests) {
          currentTest.addLast(printable);
        }
      }
    }
  }

  public synchronized void notifyStart(TestProxy root) {
    myModel = new JUnitRunningModel(root, myConsoleProperties);
    Disposer.register(this, myModel);
  }

  private static TestProxy readNode(final ObjectReader reader) {
    final TestProxy node = reader.readObject();
    final int childCount = reader.readInt();
    for (int i = 0; i < childCount; i++) {
      node.addChild(readNode(reader));
    }
    return node;
  }

  public void notifyTestStart(ObjectReader reader) {
    final TestProxy testProxy = reader.readObject();
    final JUnitRunningModel model = getModel();
    if (model != null && testProxy.getParent() == null && model.getRoot() != testProxy) {
      getDynamicParent(model, testProxy).addChild(testProxy);
    }
  }

  private TestProxy getDynamicParent(JUnitRunningModel model, final TestProxy currentTest) {
    if (myKnownDynamicParents == null) {
      myKnownDynamicParents = new HashMap<String, TestProxy>();
    }
    final String parentClass = currentTest.getInfo().getComment();
    TestProxy dynamicParent = myKnownDynamicParents.get(parentClass);
    if (dynamicParent == null) {
      final TestProxy root = findParent(parentClass, model.getRoot());
      if (root != null) {
        dynamicParent = root;
      }
      else {
        dynamicParent = new TestProxy(new ClassBasedInfo(DisplayTestInfoExtractor.FOR_CLASS) {
          {
            setClassName(parentClass);
          }

          @Override
          public void readFrom(ObjectReader reader) {
          }
        });
        model.getRoot().addChild(dynamicParent);
      }
      myKnownDynamicParents.put(parentClass, dynamicParent);
    }
    return dynamicParent;
  }

  private static TestProxy findParent(String parentClass, TestProxy root) {
    if (isAccepted(root, parentClass)) {
      return root;
    }

    for (TestProxy proxy : root.getChildren()) {
      if (isAccepted(proxy, parentClass)) {
        return proxy;
      }
    }

    return null;
  }

  private static boolean isAccepted(TestProxy root, String parentClass) {
    return Comparing.strEqual(parentClass, StringUtil.getQualifiedName(root.getInfo().getComment(), root.getName()));
  }

  public void notifyTestResult(ObjectReader reader) {
    final TestProxy testProxy = reader.readObject();

    if (testProxy.getParent() == null) { //model.getRoot() == testProxy
      getDynamicParent(myModel, testProxy).insertNextRunningChild(testProxy);
    }

    final int state = reader.readInt();
    final StateChanger stateChanger = STATE_CLASSES.get(state);
    stateChanger.changeStateOf(testProxy, reader);
    synchronized (myCurrentTests) {
      if (stateChanger instanceof RunningStateSetter) {
        myCurrentTests.add(testProxy);
      }
      else {
        myCurrentTests.remove(testProxy);
      }
    }
  }

  public void notifyFinish(ObjectReader reader) {
    myIsTerminated = true;
    synchronized (myCurrentTests) {
      myCurrentTests.clear();
    }
    final JUnitRunningModel model = getModel();
    if (model != null) {
      model.getNotifier().fireRunnerStateChanged(new CompletionEvent(true, reader.readInt()));
      terminateStillRunning(model);
    }
  }

  public synchronized boolean isRunning() {
    return !myIsTerminated;
  }

  public synchronized void setTerminated(boolean terminated) {
    myIsTerminated = terminated;
  }

  @Nullable
  public JUnitRunningModel getModel() {
    return myModel;
  }

  @Override
  public void dispose() {
    myModel = null;
  }

  public void checkTerminated() {
    if (isRunning()) {
      final JUnitRunningModel model = getModel();
      if (model != null) {
        final JUnitListenersNotifier notifier = model.getNotifier();
        if (notifier != null) {
          notifier.fireRunnerStateChanged(new CompletionEvent(false, -1));
          terminateStillRunning(model);
        }
      }
      setTerminated(true);
    }
  }

  private void terminateStillRunning(@NotNull JUnitRunningModel model) {
    if (model.getRoot() != null) {
      final List<AbstractTestProxy> runningTests = TestStateUpdater.RUNNING_LEAF.select(myModel.getRoot().getAllTests());
      for (final AbstractTestProxy runningTest : runningTests) {
        final TestProxy testProxy = (TestProxy)runningTest;
        final TestState terminated = NotFailedState.createTerminated();
        testProxy.setState(terminated);
        TestProxy parent = testProxy.getParent();
        while (parent != null) {
          parent.setState(terminated);
          parent = parent.getParent();
        }

      }
    }
  }

  private abstract static class StateChanger {
    static final Logger LOG = Logger.getInstance("#" + StateChanger.class.getName());

    abstract void changeStateOf(TestProxy testProxy, ObjectReader reader);

    void setMagnitude(final int magnitude) {
    }

    static void complete(TestProxy testProxy) {
      final int magnitude = testProxy.getState().getMagnitude();

      TestProxy parent = testProxy.getParent();
      TestProxy child = testProxy;
      while (parent != null) {
        final List<TestProxy> children = parent.getChildren();
        final TestState parentState = parent.getState();
        if (parentState instanceof SuiteState) {
          if (!child.isInProgress() && child.equals(children.get(children.size() - 1))) {
            ((SuiteState)parentState).setRunning(false);
            parent.fireStateChanged();
            parent.flush();
          }
          ((SuiteState)parentState).updateMagnitude(magnitude);
        }
        child = parent;
        parent = parent.getParent();
      }
    }
  }

  private static class RunningStateSetter extends StateChanger {
    @Override
    public void changeStateOf(final TestProxy testProxy, final ObjectReader reader) {
      testProxy.setState(TestState.RUNNING_STATE);
      TestProxy parent = testProxy.getParent();
      while (parent != null) {
        final TestState state = parent.getState();
        if (state instanceof SuiteState) {
          ((SuiteState)state).setRunning(true);
        }
        parent = parent.getParent();
      }
    }
  }

  private static class StateReader extends StateChanger {
    private final Class<? extends ReadableState> myStateClass;
    private int myInstanceMagnitude;

    public StateReader(final Class<? extends ReadableState> stateClass) {
      myStateClass = stateClass;
    }

    @Override
    public void changeStateOf(final TestProxy testProxy, final ObjectReader reader) {
      final ReadableState state;
      try {
        state = myStateClass.newInstance();
      }
      catch (Exception e) {
        LOG.error(e);
        return;
      }
      state.setMagitude(myInstanceMagnitude);
      state.initializeFrom(reader);
      testProxy.setState(state);
      complete(testProxy);
    }

    @Override
    public void setMagnitude(final int magnitude) {
      myInstanceMagnitude = magnitude;
    }
  }

  private static class TestCompleter extends StateChanger {
    @Override
    public void changeStateOf(final TestProxy testProxy, final ObjectReader reader) {
      TestState state = testProxy.getState();
      if (!testProxy.getState().isFinal()) {
        state = NotFailedState.createPassed();
      }
      testProxy.setState(state);
      testProxy.setStatistics(new Statistics(reader));
      complete(testProxy);
    }
  }

}
