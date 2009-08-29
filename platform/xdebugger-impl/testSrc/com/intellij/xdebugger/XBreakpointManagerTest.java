package com.intellij.xdebugger;

import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointAdapter;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.jetbrains.annotations.NotNull;
import org.jdom.Element;

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
    assertSame(breakpoint, assertOneElement(myBreakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE)));

    myBreakpointManager.removeBreakpoint(lineBreakpoint);
    assertSame(breakpoint, assertOneElement(myBreakpointManager.getAllBreakpoints()));
    assertTrue(myBreakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE).isEmpty());
    assertSame(breakpoint, assertOneElement(myBreakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE)));

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

    Element element = save();
    //System.out.println(JDOMUtil.writeElement(element, SystemProperties.getLineSeparator()));
    load(element);
    XBreakpoint<?>[] breakpoints = myBreakpointManager.getAllBreakpoints();
    assertEquals(2, breakpoints.length);

    XLineBreakpoint lineBreakpoint = assertInstanceOf(breakpoints[0], XLineBreakpoint.class);
    assertEquals(239, lineBreakpoint.getLine());
    assertEquals("myurl", lineBreakpoint.getFileUrl());
    assertEquals("abc", assertInstanceOf(lineBreakpoint.getProperties(), MyBreakpointProperties.class).myOption);
    assertEquals("cond", lineBreakpoint.getCondition());
    assertEquals("log", lineBreakpoint.getLogExpression());
    assertTrue(lineBreakpoint.isLogMessage());
    assertEquals(SuspendPolicy.NONE, lineBreakpoint.getSuspendPolicy());

    assertEquals("123", assertInstanceOf(breakpoints[1].getProperties(), MyBreakpointProperties.class).myOption);
    assertEquals(SuspendPolicy.ALL, breakpoints[1].getSuspendPolicy());
    assertFalse(breakpoints[1].isLogMessage());
  }

  public void testListener() throws Exception {
    final StringBuilder out = new StringBuilder();
    XBreakpointAdapter<XLineBreakpoint<MyBreakpointProperties>> listener = new XBreakpointAdapter<XLineBreakpoint<MyBreakpointProperties>>() {
      public void breakpointAdded(@NotNull final XLineBreakpoint<MyBreakpointProperties> breakpoint) {
        out.append("added[").append(breakpoint.getProperties().myOption).append("];");
      }

      public void breakpointRemoved(@NotNull final XLineBreakpoint<MyBreakpointProperties> breakpoint) {
        out.append("removed[").append(breakpoint.getProperties().myOption).append("];");
      }

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
}
