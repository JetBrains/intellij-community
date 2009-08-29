package com.intellij.xdebugger;

import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XDependentBreakpointManager;
import org.jdom.Element;

/**
 * @author nik
 */
public class XDependentBreakpointsTest extends XBreakpointsTestCase {
  private XDependentBreakpointManager myDependentBreakpointManager;


  protected void setUp() throws Exception {
    super.setUp();
    myDependentBreakpointManager = myBreakpointManager.getDependentBreakpointManager();
  }

  public void testDelete() throws Exception {
    XLineBreakpoint<?> master = createMaster();
    XLineBreakpoint<?> slave = createSlave();
    myDependentBreakpointManager.setMasterBreakpoint(slave, master, true);
    assertSame(master, myDependentBreakpointManager.getMasterBreakpoint(slave));
    assertTrue(myDependentBreakpointManager.isLeaveEnabled(slave));
    assertSame(slave, assertOneElement(myDependentBreakpointManager.getSlaveBreakpoints(master)));
    assertSame(slave, assertOneElement(myDependentBreakpointManager.getAllSlaveBreakpoints()));
    
    myBreakpointManager.removeBreakpoint(master);
    assertNull(myDependentBreakpointManager.getMasterBreakpoint(slave));
    assertEmpty(myDependentBreakpointManager.getAllSlaveBreakpoints());
  }

  public void testSerialize() throws Exception {
    XLineBreakpoint<?> master = createMaster();
    XLineBreakpoint<?> slave = createSlave();
    myDependentBreakpointManager.setMasterBreakpoint(slave, master, true);

    Element element = save();
    myDependentBreakpointManager.clearMasterBreakpoint(slave);
    //System.out.println(JDOMUtil.writeElement(element, SystemProperties.getLineSeparator()));
    load(element);

    XBreakpoint<?>[] breakpoints = myBreakpointManager.getAllBreakpoints();
    assertEquals(2, breakpoints.length);
    XLineBreakpoint newMaster = (XLineBreakpoint)breakpoints[0];
    XLineBreakpoint newSlave = (XLineBreakpoint)breakpoints[1];
    assertEquals("file://master", newMaster.getFileUrl());
    assertEquals("file://slave", newSlave.getFileUrl());
    assertSame(newMaster, myDependentBreakpointManager.getMasterBreakpoint(newSlave));
    assertTrue(myDependentBreakpointManager.isLeaveEnabled(newSlave));
  }

  private XLineBreakpoint<MyBreakpointProperties> createSlave() {
    return myBreakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, "file://slave", 2, new MyBreakpointProperties());
  }

  private XLineBreakpoint<MyBreakpointProperties> createMaster() {
    return myBreakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, "file://master", 1, new MyBreakpointProperties());
  }
}
