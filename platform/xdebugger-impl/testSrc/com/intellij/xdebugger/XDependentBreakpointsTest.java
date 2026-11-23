// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xdebugger.XBreakpointsFixture.MyBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XDependentBreakpointManager;
import org.jdom.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static com.intellij.xdebugger.XBreakpointsFixtureKt.xBreakpointsFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class XDependentBreakpointsTest {
  private final TestFixture<XBreakpointsFixture> breakpointsFixture = xBreakpointsFixture();
  private XDependentBreakpointManager myDependentBreakpointManager;

  @BeforeEach
  void setUp() {
    myDependentBreakpointManager = breakpointsFixture.get().getBreakpointManager().getDependentBreakpointManager();
  }

  @Test
  public void testDelete() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XLineBreakpoint<?> master = createMaster();
    XLineBreakpoint<?> slave = createSlave();
    myDependentBreakpointManager.setMasterBreakpoint(slave, master, true);
    assertSame(master, myDependentBreakpointManager.getMasterBreakpoint(slave));
    assertTrue(myDependentBreakpointManager.isLeaveEnabled(slave));
    assertSame(slave, assertThat(myDependentBreakpointManager.getSlaveBreakpoints(master)).singleElement().actual());
    assertSame(slave, assertThat(myDependentBreakpointManager.getAllSlaveBreakpoints()).singleElement().actual());
    
    fixture.removeBreakpoint(master);
    assertNull(myDependentBreakpointManager.getMasterBreakpoint(slave));
    assertThat(myDependentBreakpointManager.getAllSlaveBreakpoints()).isEmpty();
  }

  @Test
  public void testRemovingSlaveClearsDependency() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XLineBreakpoint<?> master = createMaster();
    XLineBreakpoint<?> slave = createSlave();
    myDependentBreakpointManager.setMasterBreakpoint(slave, master, true);

    fixture.removeBreakpoint(slave);

    assertThat(myDependentBreakpointManager.getSlaveBreakpoints(master)).isEmpty();
    assertThat(myDependentBreakpointManager.getAllSlaveBreakpoints()).isEmpty();
  }

  @Test
  public void testSetMasterBreakpointUpdatesExistingDependency() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XLineBreakpoint<?> firstMaster = createMaster();
    XLineBreakpoint<?> secondMaster =
      fixture.addLineBreakpoint("file://second-master", 3, new MyBreakpointProperties("second-master"));
    XLineBreakpoint<?> slave = createSlave();
    myDependentBreakpointManager.setMasterBreakpoint(slave, firstMaster, true);

    myDependentBreakpointManager.setMasterBreakpoint(slave, firstMaster, false);

    assertSame(firstMaster, myDependentBreakpointManager.getMasterBreakpoint(slave));
    assertFalse(myDependentBreakpointManager.isLeaveEnabled(slave));
    assertTrue(myDependentBreakpointManager.isMasterOrSlave(firstMaster));
    assertTrue(myDependentBreakpointManager.isMasterOrSlave(slave));

    myDependentBreakpointManager.setMasterBreakpoint(slave, secondMaster, true);

    assertSame(secondMaster, myDependentBreakpointManager.getMasterBreakpoint(slave));
    assertTrue(myDependentBreakpointManager.isLeaveEnabled(slave));
    assertFalse(myDependentBreakpointManager.isMasterOrSlave(firstMaster));
    assertTrue(myDependentBreakpointManager.isMasterOrSlave(secondMaster));
    assertThat(myDependentBreakpointManager.getSlaveBreakpoints(firstMaster)).isEmpty();
    assertSame(slave, assertThat(myDependentBreakpointManager.getSlaveBreakpoints(secondMaster)).singleElement().actual());
  }

  @Test
  public void testSerialize() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    XLineBreakpoint<?> master = createMaster();
    XLineBreakpoint<?> slave = createSlave();
    myDependentBreakpointManager.setMasterBreakpoint(slave, master, true);

    Element element = fixture.save();
    myDependentBreakpointManager.clearMasterBreakpoint(slave);
    //System.out.println(JDOMUtil.writeElement(element, SystemProperties.getLineSeparator()));
    fixture.load(element);

    List<XBreakpoint<?>> breakpoints = fixture.getAllBreakpoints();
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
    XBreakpointsFixture fixture = breakpointsFixture.get();
    return fixture.addLineBreakpoint("file://slave", 2, new MyBreakpointProperties("z-slave"));
  }

  private XLineBreakpoint<MyBreakpointProperties> createMaster() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    return fixture.addLineBreakpoint("file://master", 1, new MyBreakpointProperties("z-master"));
  }
}
