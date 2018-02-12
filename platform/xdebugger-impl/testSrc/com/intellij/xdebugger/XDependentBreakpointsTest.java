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
package com.intellij.xdebugger;

import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XDependentBreakpointManager;
import org.jdom.Element;

import java.util.List;

/**
 * @author nik
 */
public class XDependentBreakpointsTest extends XBreakpointsTestCase {
  private XDependentBreakpointManager myDependentBreakpointManager;


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDependentBreakpointManager = myBreakpointManager.getDependentBreakpointManager();
  }

  @Override
  protected void tearDown() throws Exception {
    myDependentBreakpointManager = null;
    super.tearDown();
  }

  public void testDelete() {
    XLineBreakpoint<?> master = createMaster();
    XLineBreakpoint<?> slave = createSlave();
    myDependentBreakpointManager.setMasterBreakpoint(slave, master, true);
    assertSame(master, myDependentBreakpointManager.getMasterBreakpoint(slave));
    assertTrue(myDependentBreakpointManager.isLeaveEnabled(slave));
    assertSame(slave, assertOneElement(myDependentBreakpointManager.getSlaveBreakpoints(master)));
    assertSame(slave, assertOneElement(myDependentBreakpointManager.getAllSlaveBreakpoints()));
    
    removeBreakPoint(myBreakpointManager, master);
    assertNull(myDependentBreakpointManager.getMasterBreakpoint(slave));
    assertEmpty(myDependentBreakpointManager.getAllSlaveBreakpoints());
  }

  public void testSerialize() {
    XLineBreakpoint<?> master = createMaster();
    XLineBreakpoint<?> slave = createSlave();
    myDependentBreakpointManager.setMasterBreakpoint(slave, master, true);

    Element element = save();
    myDependentBreakpointManager.clearMasterBreakpoint(slave);
    //System.out.println(JDOMUtil.writeElement(element, SystemProperties.getLineSeparator()));
    load(element);

    List<XBreakpoint<?>> breakpoints = getAllBreakpoints();
    assertEquals(3, breakpoints.size());
    assertEquals("default", ((MyBreakpointProperties)breakpoints.get(0).getProperties()).myOption);
    XLineBreakpoint newMaster = (XLineBreakpoint)breakpoints.get(1);
    XLineBreakpoint newSlave = (XLineBreakpoint)breakpoints.get(2);
    assertEquals("file://master", newMaster.getFileUrl());
    assertEquals("file://slave", newSlave.getFileUrl());
    assertSame(newMaster, myDependentBreakpointManager.getMasterBreakpoint(newSlave));
    assertTrue(myDependentBreakpointManager.isLeaveEnabled(newSlave));
  }

  private XLineBreakpoint<MyBreakpointProperties> createSlave() {
    return addLineBreakpoint(myBreakpointManager, "file://slave", 2, new MyBreakpointProperties("z-slave"));
  }

  private XLineBreakpoint<MyBreakpointProperties> createMaster() {
    return addLineBreakpoint(myBreakpointManager, "file://master", 1, new MyBreakpointProperties("z-master"));
  }
}
