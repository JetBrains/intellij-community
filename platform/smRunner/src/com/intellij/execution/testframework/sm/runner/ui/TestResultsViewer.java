package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 */
public interface TestResultsViewer extends Disposable {
  /**
   * Fake Root for toplevel test suits/tests
   * @return root
   */
  SMTestProxy getTestsRootNode();

  /**
   * Selects test or suite in Tests tree and notify about selection changed
   * @param proxy
   */
  void selectAndNotify(@Nullable AbstractTestProxy proxy);

  void addEventsListener(EventsListener listener);

  void setShowStatisticForProxyHandler(PropagateSelectionHandler handler);

  /**
   * If handler for statistics was set this method will execute it
   */
  void showStatisticsForSelectedProxy();

  interface EventsListener extends TestProxyTreeSelectionListener {
    void onTestingStarted(TestResultsViewer sender);
    void onTestingFinished(TestResultsViewer sender);
    void onTestNodeAdded(TestResultsViewer sender, SMTestProxy test);
  }
}
