// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.BreakpointManagerState;
import com.intellij.xdebugger.impl.breakpoints.BreakpointState;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnit4.class)
public class XBreakpointManagerTest extends XBreakpointsTestCase {

  @Rule
  public TestRule timeout = new DisableOnDebug(Timeout.seconds(30));

  @Test
  public void testAddRemove() {
    Set<XBreakpoint<MyBreakpointProperties>> defaultBreakpoints = myBreakpointManager.getDefaultBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE);
    assertOneElement(defaultBreakpoints);
    XBreakpoint<MyBreakpointProperties> defaultBreakpoint = ContainerUtil.getOnlyItem(defaultBreakpoints);
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

  @Test
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
    assertEquals("cond", lineBreakpoint.getConditionExpression().getExpression());
    assertEquals("log", lineBreakpoint.getLogExpressionObject().getExpression());
    assertTrue(lineBreakpoint.isLogMessage());
    assertEquals(SuspendPolicy.NONE, lineBreakpoint.getSuspendPolicy());

    assertEquals("z2", assertInstanceOf(breakpoints.get(2).getProperties(), MyBreakpointProperties.class).myOption);
    assertEquals(SuspendPolicy.ALL, breakpoints.get(2).getSuspendPolicy());
    assertFalse(breakpoints.get(2).isLogMessage());
  }

  @Test
  public void testDoNotSaveUnmodifiedDefaultBreakpoint() {
    reload();

    assertThat(getSingleBreakpoint().getProperties().myOption).isEqualTo("default");
    Element element = save();
    assertThat(element).isNull();
  }

  @Test
  public void testSaveChangedDefaultBreakpoint() {
    reload();
    final XBreakpoint<MyBreakpointProperties> breakpoint = getSingleBreakpoint();
    breakpoint.setEnabled(false);

    assertFalse(save().getContent().isEmpty());
    reload();
    assertFalse(getSingleBreakpoint().isEnabled());
  }

  @Test
  public void testSaveDefaultBreakpointWithModifiedProperties() {
    reload();
    getSingleBreakpoint().getProperties().myOption = "changed";

    assertFalse(save().getContent().isEmpty());
    reload();
    assertEquals("changed", getSingleBreakpoint().getProperties().myOption);
  }

  @Test
  public void testLoadStateDeduplicatesDefaultBreakpointsWithSameState() {
    BreakpointManagerState state = new BreakpointManagerState();
    BreakpointState first = createDefaultState(MY_SIMPLE_BREAKPOINT_TYPE.getId(), true, SuspendPolicy.NONE, 1);
    BreakpointState second = createDefaultState(MY_SIMPLE_BREAKPOINT_TYPE.getId(), true, SuspendPolicy.NONE, 2);
    state.getDefaultBreakpoints().add(first);
    state.getDefaultBreakpoints().add(second);

    myBreakpointManager.loadState(state);

    XBreakpoint<MyBreakpointProperties> breakpoint = assertOneElement(myBreakpointManager.getDefaultBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE));
    assertEquals(1, myBreakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE).size());
    assertTrue(breakpoint.isEnabled());
    assertEquals(SuspendPolicy.NONE, breakpoint.getSuspendPolicy());
  }

  @Test
  public void testLoadStateKeepsDefaultBreakpointsWithDifferentStates() {
    BreakpointManagerState state = new BreakpointManagerState();
    BreakpointState first = createDefaultState(MY_SIMPLE_BREAKPOINT_TYPE.getId(), true, SuspendPolicy.NONE);
    BreakpointState second = createDefaultState(MY_SIMPLE_BREAKPOINT_TYPE.getId(), false, SuspendPolicy.ALL);
    state.getDefaultBreakpoints().add(first);
    state.getDefaultBreakpoints().add(second);

    myBreakpointManager.loadState(state);

    Set<XBreakpoint<MyBreakpointProperties>> breakpoints = myBreakpointManager.getDefaultBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE);
    assertEquals(2, breakpoints.size());
    assertEquals(2, myBreakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE).size());
    assertTrue(
      ContainerUtil.exists(breakpoints, breakpoint -> breakpoint.isEnabled() && breakpoint.getSuspendPolicy() == SuspendPolicy.NONE));
    assertTrue(
      ContainerUtil.exists(breakpoints, breakpoint -> !breakpoint.isEnabled() && breakpoint.getSuspendPolicy() == SuspendPolicy.ALL));
  }

  @Test
  public void testExtensionAddedAddsDefaultBreakpointWhenStatesDiffer() {
    XBreakpointType<XBreakpoint<MyBreakpointProperties>, MyBreakpointProperties> type =
      createExtensionType("from-extension", SuspendPolicy.NONE);

    myBreakpointManager.addDefaultBreakpoint(type, new MyBreakpointProperties("existing"));
    assertEquals(1, myBreakpointManager.getDefaultBreakpoints(type).size());

    XBreakpointType.EXTENSION_POINT_NAME.getPoint().registerExtension(type, getTestRootDisposable());

    Set<XBreakpoint<MyBreakpointProperties>> breakpoints = myBreakpointManager.getDefaultBreakpoints(type);
    assertEquals(2, breakpoints.size());
    assertTrue(ContainerUtil.exists(breakpoints, breakpoint -> {
      MyBreakpointProperties properties = assertInstanceOf(breakpoint.getProperties(), MyBreakpointProperties.class);
      return "existing".equals(properties.myOption);
    }));
    assertTrue(ContainerUtil.exists(breakpoints, breakpoint -> {
      MyBreakpointProperties properties = assertInstanceOf(breakpoint.getProperties(), MyBreakpointProperties.class);
      return "from-extension".equals(properties.myOption) && breakpoint.getSuspendPolicy() == SuspendPolicy.NONE;
    }));
  }

  @Test
  public void testExtensionAddedDeduplicatesDefaultBreakpointWithSameState() {
    XBreakpointType<XBreakpoint<MyBreakpointProperties>, MyBreakpointProperties> type =
      createExtensionType("from-extension", SuspendPolicy.NONE);

    XBreakpoint<MyBreakpointProperties> existing = myBreakpointManager.addDefaultBreakpoint(type, new MyBreakpointProperties("from-extension"));
    existing.setEnabled(true);
    existing.setSuspendPolicy(SuspendPolicy.NONE);
    assertEquals(1, myBreakpointManager.getDefaultBreakpoints(type).size());

    XBreakpointType.EXTENSION_POINT_NAME.getPoint().registerExtension(type, getTestRootDisposable());

    Set<XBreakpoint<MyBreakpointProperties>> breakpoints = myBreakpointManager.getDefaultBreakpoints(type);
    assertOneElement(breakpoints);
    XBreakpoint<MyBreakpointProperties> breakpoint = assertOneElement(breakpoints);
    assertEquals("from-extension", assertInstanceOf(breakpoint.getProperties(), MyBreakpointProperties.class).myOption);
    assertTrue(breakpoint.isEnabled());
    assertEquals(SuspendPolicy.NONE, breakpoint.getSuspendPolicy());
  }

  @Test
  public void testListener() {
    final StringBuilder out = new StringBuilder();
    XBreakpointListener<XLineBreakpoint<MyBreakpointProperties>> listener = new XBreakpointListener<>() {
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

  @Test
  public void testRemoveFile() {
    VirtualFile file = getTempDir().createVirtualFile("breakpoint.txt");
    addLineBreakpoint(myBreakpointManager, file.getUrl(), 0, null);
    assertOneElement(myBreakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE));
    delete(file);
    assertEmpty(myBreakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE));
  }

  @Test
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
    load(JDOMUtil.load(oldStyle));
    XLineBreakpoint<MyBreakpointProperties> breakpoint = assertOneElement(myBreakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE));
    assertEquals(condition, breakpoint.getConditionExpression().getExpression());
    assertEquals(logExpression, breakpoint.getLogExpressionObject().getExpression());
  }

  private XBreakpoint<MyBreakpointProperties> getSingleBreakpoint() {
    return assertOneElement(myBreakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE));
  }

  private static @NotNull BreakpointState createDefaultState(@NotNull String typeId,
                                                             boolean enabled,
                                                             @NotNull SuspendPolicy suspendPolicy) {
    BreakpointState state = new BreakpointState();
    state.setTypeId(typeId);
    state.setEnabled(enabled);
    state.setSuspendPolicy(suspendPolicy);
    return state;
  }

  private static @NotNull BreakpointState createDefaultState(@NotNull String typeId,
                                                             boolean enabled,
                                                             @NotNull SuspendPolicy suspendPolicy,
                                                             long timeStamp) {
    BreakpointState state = createDefaultState(typeId, enabled, suspendPolicy);
    state.setTimeStamp(timeStamp);
    return state;
  }

  private static @NotNull XBreakpointType<XBreakpoint<MyBreakpointProperties>, MyBreakpointProperties> createExtensionType(
    @NotNull String defaultOption,
    @NotNull SuspendPolicy suspendPolicy
  ) {
    String typeId = "testExtensionType" + System.nanoTime();
    return new XBreakpointType<>(typeId, "239") {
      @Override
      public String getDisplayText(XBreakpoint<MyBreakpointProperties> breakpoint) {
        return "";
      }

      @Override
      public MyBreakpointProperties createProperties() {
        return new MyBreakpointProperties();
      }

      @Override
      public XBreakpoint<MyBreakpointProperties> createDefaultBreakpoint(@NotNull XBreakpointCreator<MyBreakpointProperties> creator) {
        XBreakpoint<MyBreakpointProperties> breakpoint = creator.createBreakpoint(new MyBreakpointProperties(defaultOption));
        breakpoint.setEnabled(true);
        breakpoint.setSuspendPolicy(suspendPolicy);
        return breakpoint;
      }
    };
  }

  private void reload() {
    Element element = save();
    load(element);
  }
}
