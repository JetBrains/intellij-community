/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.mock.MockApplication;
import com.intellij.mock.MockProject;
import com.intellij.mock.MockProjectEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BusyObject;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: kirillk
 * Date: 8/17/11
 * Time: 10:04 AM
 * To change this template use File | Settings | File Templates.
 */
public class ActivityMonitorTest extends UsefulTestCase {
  private UiActivityMonitorImpl myMonitor;
  private ModalityState myCurrentState;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myCurrentState = ModalityState.NON_MODAL;
    final ModalityStateEx any = new ModalityStateEx();
    Extensions.registerAreaClass("IDEA_PROJECT", null);
    ApplicationManager.setApplication(new MockApplication(getTestRootDisposable()) {
      @NotNull
      @Override
      public ModalityState getCurrentModalityState() {
        return myCurrentState;
      }

      @Override
      public ModalityState getAnyModalityState() {
        return any;
      }
    }, getTestRootDisposable());
    myMonitor = new UiActivityMonitorImpl();
    myMonitor.setActive(true);
    disposeOnTearDown(myMonitor);
  }

  public void testReady() {
    assertReady(null);

    MockProject project1 = new MockProjectEx(getTestRootDisposable());
    assertReady(project1);

    MockProject project2 = new MockProjectEx(getTestRootDisposable());
    assertReady(project2);

    myMonitor.initBusyObjectFor(project1);
    assertTrue(myMonitor.hasObjectFor(project1));

    myMonitor.initBusyObjectFor(project2);
    assertTrue(myMonitor.hasObjectFor(project2));


    myMonitor.addActivity(new UiActivity("global"), ModalityState.any());
    assertBusy(null);
    assertBusy(project1);
    assertBusy(project2);

    myMonitor.addActivity(new UiActivity("global"), ModalityState.any());
    assertBusy(null);
    assertBusy(project1);
    assertBusy(project2);

    myMonitor.removeActivity(new UiActivity("global"));
    assertReady(null);
    assertReady(project1);
    assertReady(project2);


    myMonitor.addActivity(project1, new UiActivity("p1"), ModalityState.any());
    assertReady(null);
    assertBusy(project1);
    assertReady(project2);

    myMonitor.addActivity(new UiActivity("global"), ModalityState.any());
    assertBusy(null);
    assertBusy(project1);
    assertBusy(project2);

    myMonitor.removeActivity(new UiActivity("global"));
    assertReady(null);
    assertBusy(project1);
    assertReady(project2);

    myMonitor.removeActivity(project1, new UiActivity("p1"));
    assertReady(null);
    assertReady(project1);
    assertReady(project2);
  }

  public void testReadyWithWatchActivities() throws Exception {
    final UiActivity root = new UiActivity("root");

    final UiActivity op1 = new UiActivity("root", "operation1");
    final UiActivity op2 = new UiActivity("root", "operation2");

    final UiActivity op12 = new UiActivity("root", "operation1", "operation12");
    final UiActivity op121 = new UiActivity("root", "operation1", "operation12", "operation121");


    myMonitor.addActivity(op1);
    assertBusy(null);
    assertReady(null, op2);
    assertBusy(null, op1);
    assertBusy(null, root);

    myMonitor.removeActivity(op1);
    assertReady(null);
    assertReady(null, op2);
    assertReady(null, op1);
    assertReady(null, root);

    myMonitor.addActivity(op12);
    assertBusy(null);
    assertBusy(null, root);
    assertBusy(null, op12);
    assertReady(null, op121);
  }

  public void testModalityState() {
    assertReady(null);

    myMonitor.addActivity(new UiActivity("non_modal_1"), ModalityState.NON_MODAL);
    assertBusy(null);

    myCurrentState = new ModalityStateEx(new Object[] {"dialog"});
    assertReady(null);

    myMonitor.addActivity(new UiActivity("non_modal2"), ModalityState.NON_MODAL);
    assertReady(null);

    myMonitor.addActivity(new UiActivity("modal_1"), new ModalityStateEx(new Object[] {"dialog"}));
    assertBusy(null);

    myMonitor.addActivity(new UiActivity("modal_2"), new ModalityStateEx(new Object[] {"dialog", "popup"}));
    assertBusy(null);

    myCurrentState = ModalityState.NON_MODAL;
    assertBusy(null);
  }

  public void testModalityStateAny() {
    assertReady(null);

    myMonitor.addActivity(new UiActivity("non_modal_1"), ModalityState.any());
    assertBusy(null);

    myCurrentState = new ModalityStateEx(new Object[] {"dialog"});
    assertBusy(null);
  }

  public void testUiActivity() throws Exception {
    assertTrue(new UiActivity("root", "folder1").isSameOrGeneralFor(new UiActivity("root", "folder1")));
    assertTrue(new UiActivity("root", "folder1").isSameOrGeneralFor(new UiActivity("root", "folder1", "folder2")));
    assertFalse(new UiActivity("root", "folder2").isSameOrGeneralFor(new UiActivity("root", "folder1", "folder2")));
    assertFalse(new UiActivity("root", "folder2").isSameOrGeneralFor(new UiActivity("anotherRoot")));
  }
  
  private void assertReady(@Nullable Project key, UiActivity ... activities) {
    BusyObject.Impl busy = (BusyObject.Impl)(key != null ? myMonitor.getBusy(key, activities) : myMonitor.getBusy(activities));
    assertTrue("Must be READY, but was: BUSY", busy.isReady());
    
    final boolean[] done = new boolean[] {false};
    busy.getReady(this).doWhenDone(new Runnable() {
      @Override
      public void run() {
        done[0] = true;
      }
    });

    assertTrue(done[0]);
  }

  private void assertBusy(@Nullable Project key, UiActivity ... activities) {
    BusyObject.Impl busy = (BusyObject.Impl)(key != null ? myMonitor.getBusy(key, activities) : myMonitor.getBusy(activities));
    assertFalse("Must be BUSY, but was: READY", busy.isReady());
  }

}
