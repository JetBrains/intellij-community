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

import com.intellij.mock.MockProject;
import com.intellij.mock.MockProjectEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.BusyObject;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: kirillk
 * Date: 8/17/11
 * Time: 10:04 AM
 * To change this template use File | Settings | File Templates.
 */
public class ActivityMonitorTest extends TestCase {


  private UiActivityMonitor myMonitor;

  @Override
  protected void setUp() throws Exception {
    myMonitor = new UiActivityMonitor();
  }

  public void testReady() {
    assertReady(null);

    MockProjectEx project1 = new MockProjectEx();
    assertReady(project1);
    assertFalse(myMonitor.hasObjectFor(project1));

    MockProjectEx project2 = new MockProjectEx();
    assertReady(project2);
    assertFalse(myMonitor.hasObjectFor(project2));

    myMonitor.initBusyObjectFor(project1);
    assertTrue(myMonitor.hasObjectFor(project1));

    myMonitor.initBusyObjectFor(project2);
    assertTrue(myMonitor.hasObjectFor(project2));


    myMonitor.addActivity("global");
    assertBusy(null);
    assertBusy(project1);
    assertBusy(project2);

    myMonitor.addActivity("global");
    assertBusy(null);
    assertBusy(project1);
    assertBusy(project2);

    myMonitor.removeActivity("global");
    assertReady(null);
    assertReady(project1);
    assertReady(project2);


    myMonitor.addActivity(project1, "p1");
    assertBusy(null);
    assertBusy(project1);
    assertReady(project2);

    myMonitor.addActivity("global");
    assertBusy(null);
    assertBusy(project1);
    assertBusy(project2);

    myMonitor.removeActivity("global");
    assertBusy(null);
    assertBusy(project1);
    assertReady(project2);

    myMonitor.removeActivity(project1, "p1");
    assertReady(null);
    assertReady(project1);
    assertReady(project2);
  }
  
  private void assertReady(@Nullable Project key) {
    BusyObject.Impl busy = (BusyObject.Impl)(key != null ? myMonitor.getBusy(key) : myMonitor.getBusy());
    assertTrue(busy.isReady());
    
    final boolean[] done = new boolean[] {false};
    busy.getReady(this).doWhenDone(new Runnable() {
      @Override
      public void run() {
        done[0] = true;
      }
    });

    assertTrue(done[0]);
  }

  private void assertBusy(@Nullable Project key) {
    BusyObject.Impl busy = (BusyObject.Impl)(key != null ? myMonitor.getBusy(key) : myMonitor.getBusy());
    assertFalse(busy.isReady());
  }
}
