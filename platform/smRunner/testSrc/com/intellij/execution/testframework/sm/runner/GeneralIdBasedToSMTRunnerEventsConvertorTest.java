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
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent;
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

public class GeneralIdBasedToSMTRunnerEventsConvertorTest extends BaseSMTRunnerTestCase {
  private GeneralIdBasedToSMTRunnerEventsConvertor myEventsProcessor;
  private SMTestProxy.SMRootTestProxy myRootProxy;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRootProxy = new SMTestProxy.SMRootTestProxy();
    myEventsProcessor = new GeneralIdBasedToSMTRunnerEventsConvertor(getProject(), myRootProxy, "test");
    myEventsProcessor.onStartTesting();
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myEventsProcessor);
    super.tearDown();
  }

  public void testOnStartedTesting() {
    assertTrue(myRootProxy.wasLaunched());
    assertTrue(myRootProxy.isInProgress());
    assertTrue(myRootProxy.isLeaf());
  }

  public void testOnTestStarted() throws InterruptedException {
    onTestStarted("my test", 1, 0);
    SMTestProxy proxy = myEventsProcessor.findProxyById(1);

    assertNotNull(proxy);
    assertTrue(proxy.isInProgress());
    assertEquals(1, myRootProxy.getChildren().size());

    onTestFailed(1, "", 1);
    proxy = myEventsProcessor.findProxyById(1);

    assertNotNull(proxy);
    assertTrue(proxy.isDefect());
    assertEquals(Long.valueOf(1), proxy.getDuration());
  }

  private void onTestStarted(@NotNull String testName, int id, int parentId) {
    myEventsProcessor.onTestStarted(new TestStartedEvent(testName, id, parentId, null, null, null, true));
  }

  private void onTestFailed(int id, @NotNull String errorMessage, int duration) {
    myEventsProcessor.onTestFailure(new TestFailedEvent(null, id, errorMessage, null, false, null, null, null, duration));
  }

}
