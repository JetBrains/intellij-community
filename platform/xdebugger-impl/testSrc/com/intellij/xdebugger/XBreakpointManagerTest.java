/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.JdomKt;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author nik
 */
public class XBreakpointManagerTest extends XBreakpointsTestCase {

  public void testAddRemove() {
    XBreakpoint<MyBreakpointProperties> defaultBreakpoint = myBreakpointManager.getDefaultBreakpoint(MY_SIMPLE_BREAKPOINT_TYPE);
    assertSameElements(getAllBreakpoints(), defaultBreakpoint);

    XLineBreakpoint<MyBreakpointProperties> lineBreakpoint =
      addLineBreakpoint(myBreakpointManager, "url", 239, new MyBreakpointProperties("123"));

    XBreakpoint<MyBreakpointProperties> breakpoint = addBreakpoint(myBreakpointManager, new MyBreakpointProperties("abc"));

    assertSameElements(getAllBreakpoints(), breakpoint, lineBreakpoint, defaultBreakpoint);
    assertSame(lineBreakpoint, assertOneElement(myBreakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE)));
    assertSameElements(myBreakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE), breakpoint, defaultBreakpoint);

    removeBreakPoint(myBreakpointManager, lineBreakpoint);
    assertSameElements(getAllBreakpoints(), breakpoint, defaultBreakpoint);
    assertTrue(myBreakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE).isEmpty());
    assertSameElements(myBreakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE), breakpoint, defaultBreakpoint);

    removeBreakPoint(myBreakpointManager, breakpoint);
    assertSameElements(getAllBreakpoints(), defaultBreakpoint);
    assertSameElements(myBreakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE), defaultBreakpoint);
  }

  public void testSerialize() {
    XLineBreakpoint<MyBreakpointProperties> breakpoint =
      addLineBreakpoint(myBreakpointManager, "myurl", 239, new MyBreakpointProperties("z1"));
    breakpoint.setCondition("cond");
    breakpoint.setLogExpression("log");
    breakpoint.setSuspendPolicy(SuspendPolicy.NONE);
    breakpoint.setLogMessage(true);
    addBreakpoint(myBreakpointManager, new MyBreakpointProperties("z2"));

    reload();
    List<XBreakpoint<?>> breakpoints = getAllBreakpoints();
    assertEquals("Expected 3 breakpoints, actual: " + breakpoints, 3, breakpoints.size());

    assertTrue(myBreakpointManager.isDefaultBreakpoint(breakpoints.get(0)));
    assertEquals("default", assertInstanceOf(breakpoints.get(0).getProperties(), MyBreakpointProperties.class).myOption);
    assertTrue(breakpoints.get(0).isEnabled());

    XLineBreakpoint lineBreakpoint = assertInstanceOf(breakpoints.get(1), XLineBreakpoint.class);
    assertEquals(239, lineBreakpoint.getLine());
    assertEquals("myurl", lineBreakpoint.getFileUrl());
    assertEquals("z1", assertInstanceOf(lineBreakpoint.getProperties(), MyBreakpointProperties.class).myOption);
    assertEquals("cond", lineBreakpoint.getCondition());
    assertEquals("log", lineBreakpoint.getLogExpression());
    assertTrue(lineBreakpoint.isLogMessage());
    assertEquals(SuspendPolicy.NONE, lineBreakpoint.getSuspendPolicy());

    assertEquals("z2", assertInstanceOf(breakpoints.get(2).getProperties(), MyBreakpointProperties.class).myOption);
    assertEquals(SuspendPolicy.ALL, breakpoints.get(2).getSuspendPolicy());
    assertFalse(breakpoints.get(2).isLogMessage());
  }

  public void testDoNotSaveUnmodifiedDefaultBreakpoint() {
    reload();

    assertThat(getSingleBreakpoint().getProperties().myOption).isEqualTo("default");
    Element element = save();
    assertThat(element).isNull();
  }

  public void testSaveChangedDefaultBreakpoint() {
    reload();
    final XBreakpoint<MyBreakpointProperties> breakpoint = getSingleBreakpoint();
    breakpoint.setEnabled(false);

    assertFalse(save().getContent().isEmpty());
    reload();
    assertFalse(getSingleBreakpoint().isEnabled());
  }

  public void testSaveDefaultBreakpointWithModifiedProperties() {
    reload();
    getSingleBreakpoint().getProperties().myOption = "changed";

    assertFalse(save().getContent().isEmpty());
    reload();
    assertEquals("changed", getSingleBreakpoint().getProperties().myOption);
  }

  public void testListener() {
    final StringBuilder out = new StringBuilder();
    XBreakpointListener<XLineBreakpoint<MyBreakpointProperties>> listener = new XBreakpointListener<XLineBreakpoint<MyBreakpointProperties>>() {
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

    XBreakpoint<MyBreakpointProperties> breakpoint = addLineBreakpoint(myBreakpointManager, "url", 239, new MyBreakpointProperties("abc"));
    addBreakpoint(myBreakpointManager, new MyBreakpointProperties("321"));
    removeBreakPoint(myBreakpointManager, breakpoint);
    assertEquals("added[abc];removed[abc];", out.toString());

    myBreakpointManager.removeBreakpointListener(MY_LINE_BREAKPOINT_TYPE, listener);
    out.setLength(0);
    addLineBreakpoint(myBreakpointManager, "url", 239, new MyBreakpointProperties("a"));
    assertEquals("", out.toString());
  }

  public void testRemoveFile() {
    final VirtualFile file = myTempFiles.createVFile("breakpoint", ".txt");
    addLineBreakpoint(myBreakpointManager, file.getUrl(), 0, null);
    assertOneElement(myBreakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE));
    delete(file);
    assertEmpty(myBreakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE));
  }

  public void testConditionConvert() throws IOException, JDOMException {
    String condition = "old-style condition";
    String logExpression = "old-style expression";
    String oldStyle =
    "<breakpoint-manager>" +
    "<breakpoints>" +
    "<line-breakpoint enabled=\"true\" type=\"" + MY_LINE_BREAKPOINT_TYPE.getId() + "\">" +
    "      <condition>" + condition + "</condition>" +
    "      <url>url</url>" +
    "      <log-expression>" + logExpression + "</log-expression>" +
    "</line-breakpoint>" +
    "</breakpoints>" +
    "<option name=\"time\" value=\"1\" />" +
    "</breakpoint-manager>";
    load(JdomKt.loadElement(oldStyle));
    XLineBreakpoint<MyBreakpointProperties> breakpoint = assertOneElement(myBreakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE));
    assertEquals(condition, breakpoint.getCondition());
    assertEquals(logExpression, breakpoint.getLogExpression());
  }

  private XBreakpoint<MyBreakpointProperties> getSingleBreakpoint() {
    return assertOneElement(myBreakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE));
  }

  private void reload() {
    Element element = save();
    load(element);
  }
}
