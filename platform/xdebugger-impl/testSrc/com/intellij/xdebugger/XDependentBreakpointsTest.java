// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XDependentBreakpointManager;
import org.jdom.Element;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class XDependentBreakpointsTest extends XBreakpointsTestCase {
  private XDependentBreakpointManager myDependentBreakpointManager;

  @Rule
  public TestRule timeout = new DisableOnDebug(Timeout.seconds(30));

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

  @Test
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

  @Test
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
