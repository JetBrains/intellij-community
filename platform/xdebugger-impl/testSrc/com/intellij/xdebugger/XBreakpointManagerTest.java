/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointAdapter;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author nik
 */
public class XBreakpointManagerTest extends XBreakpointsTestCase {

  public void testAddRemove() throws Exception {
    XLineBreakpoint<MyBreakpointProperties> lineBreakpoint =
      myBreakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, "url", 239, new MyBreakpointProperties("123"));

    XBreakpoint<MyBreakpointProperties> breakpoint = myBreakpointManager.addBreakpoint(MY_SIMPLE_BREAKPOINT_TYPE, new MyBreakpointProperties("abc"));

    assertSameElements(myBreakpointManager.getAllBreakpoints(), breakpoint, lineBreakpoint);
    assertSame(lineBreakpoint, assertOneElement(myBreakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE)));
    assertSame(breakpoint, getSingleBreakpoint());

    myBreakpointManager.removeBreakpoint(lineBreakpoint);
    assertSame(breakpoint, assertOneElement(myBreakpointManager.getAllBreakpoints()));
    assertTrue(myBreakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE).isEmpty());
    assertSame(breakpoint, getSingleBreakpoint());

    myBreakpointManager.removeBreakpoint(breakpoint);
    assertEquals(0, myBreakpointManager.getAllBreakpoints().length);
    assertTrue(myBreakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE).isEmpty());
  }

  public void testSerialize() throws Exception {
    XLineBreakpoint<MyBreakpointProperties> breakpoint =
      myBreakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, "myurl", 239, new MyBreakpointProperties("abc"));
    breakpoint.setCondition("cond");
    breakpoint.setLogExpression("log");
    breakpoint.setSuspendPolicy(SuspendPolicy.NONE);
    breakpoint.setLogMessage(true);
    myBreakpointManager.addBreakpoint(MY_SIMPLE_BREAKPOINT_TYPE, new MyBreakpointProperties("123"));

    reload();
    XBreakpoint<?>[] breakpoints = myBreakpointManager.getAllBreakpoints();
    assertEquals("Expected 3 breakpoints, actual: " + Arrays.toString(breakpoints), 3, breakpoints.length);

    assertTrue(myBreakpointManager.isDefaultBreakpoint(breakpoints[0]));
    assertEquals("default", assertInstanceOf(breakpoints[0].getProperties(), MyBreakpointProperties.class).myOption);
    assertTrue(breakpoints[0].isEnabled());

    XLineBreakpoint lineBreakpoint = assertInstanceOf(breakpoints[1], XLineBreakpoint.class);
    assertEquals(239, lineBreakpoint.getLine());
    assertEquals("myurl", lineBreakpoint.getFileUrl());
    assertEquals("abc", assertInstanceOf(lineBreakpoint.getProperties(), MyBreakpointProperties.class).myOption);
    assertEquals("cond", lineBreakpoint.getCondition());
    assertEquals("log", lineBreakpoint.getLogExpression());
    assertTrue(lineBreakpoint.isLogMessage());
    assertEquals(SuspendPolicy.NONE, lineBreakpoint.getSuspendPolicy());

    assertEquals("123", assertInstanceOf(breakpoints[2].getProperties(), MyBreakpointProperties.class).myOption);
    assertEquals(SuspendPolicy.ALL, breakpoints[2].getSuspendPolicy());
    assertFalse(breakpoints[2].isLogMessage());
  }

  public void testDoNotSaveUnmodifiedDefaultBreakpoint() throws Exception {
    reload();

    assertEquals("default", getSingleBreakpoint().getProperties().myOption);
    Element element = save();
    assertEquals(0, element.getContent().size());
  }

  public void testSaveChangedDefaultBreakpoint() throws Exception {
    reload();
    final XBreakpoint<MyBreakpointProperties> breakpoint = getSingleBreakpoint();
    breakpoint.setEnabled(false);

    assertFalse(save().getContent().isEmpty());
    reload();
    assertFalse(getSingleBreakpoint().isEnabled());
  }

  public void testSaveDefaultBreakpointWithModifiedProperties() throws Exception {
    reload();
    getSingleBreakpoint().getProperties().myOption = "changed";

    assertFalse(save().getContent().isEmpty());
    reload();
    assertEquals("changed", getSingleBreakpoint().getProperties().myOption);
  }

  public void testListener() throws Exception {
    final StringBuilder out = new StringBuilder();
    XBreakpointAdapter<XLineBreakpoint<MyBreakpointProperties>> listener = new XBreakpointAdapter<XLineBreakpoint<MyBreakpointProperties>>() {
      @Override
      public void breakpointAdded(@NotNull final XLineBreakpoint<MyBreakpointProperties> breakpoint) {
        out.append("added[").append(breakpoint.getProperties().myOption).append("];");
      }

      @Override
      public void breakpointRemoved(@NotNull final XLineBreakpoint<MyBreakpointProperties> breakpoint) {
        out.append("removed[").append(breakpoint.getProperties().myOption).append("];");
      }

      @Override
      public void breakpointChanged(@NotNull final XLineBreakpoint<MyBreakpointProperties> breakpoint) {
        out.append("changed[").append(breakpoint.getProperties().myOption).append("];");
      }
    };
    myBreakpointManager.addBreakpointListener(MY_LINE_BREAKPOINT_TYPE, listener);

    XBreakpoint<MyBreakpointProperties> breakpoint = myBreakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, "url", 239, new MyBreakpointProperties("abc"));
    myBreakpointManager.addBreakpoint(MY_SIMPLE_BREAKPOINT_TYPE, new MyBreakpointProperties("321"));
    myBreakpointManager.removeBreakpoint(breakpoint);
    assertEquals("added[abc];removed[abc];", out.toString());

    myBreakpointManager.removeBreakpointListener(MY_LINE_BREAKPOINT_TYPE, listener);
    out.setLength(0);
    myBreakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, "url", 239, new MyBreakpointProperties("a"));
    assertEquals("", out.toString());
  }

  private XBreakpoint<MyBreakpointProperties> getSingleBreakpoint() {
    return assertOneElement(myBreakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE));
  }

  private void reload() {
    Element element = save();
    load(element);
  }
}
