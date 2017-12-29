/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XDebuggerTestCase extends PlatformTestCase {
  public static final MyLineBreakpointType MY_LINE_BREAKPOINT_TYPE = new MyLineBreakpointType();
  protected static final MySimpleBreakpointType MY_SIMPLE_BREAKPOINT_TYPE = new MySimpleBreakpointType();

  @NotNull
  static XBreakpoint<MyBreakpointProperties> addBreakpoint(XBreakpointManagerImpl breakpointManager, MyBreakpointProperties abc) {
    return WriteAction.compute(() -> breakpointManager.addBreakpoint(MY_SIMPLE_BREAKPOINT_TYPE, abc));
  }

  @NotNull
  static XLineBreakpoint<MyBreakpointProperties> addLineBreakpoint(XBreakpointManagerImpl breakpointManager,
                                                                   String url,
                                                                   int line,
                                                                   MyBreakpointProperties properties) {
    return WriteAction.compute(() -> breakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, url, line, properties));
  }

  static void removeBreakPoint(XBreakpointManagerImpl breakpointManager, XBreakpoint<?> breakpoint) {
    WriteAction.run(() -> breakpointManager.removeBreakpoint(breakpoint));
  }

  @Override
  protected void initApplication() throws Exception {
    super.initApplication();
    final ExtensionPoint<XBreakpointType> point = getBreakpointTypes();
    point.registerExtension(MY_LINE_BREAKPOINT_TYPE);
    point.registerExtension(MY_SIMPLE_BREAKPOINT_TYPE);
  }

  private static ExtensionPoint<XBreakpointType> getBreakpointTypes() {
    return Extensions.getRootArea().getExtensionPoint(XBreakpointType.EXTENSION_POINT_NAME);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      getBreakpointTypes().unregisterExtension(MY_LINE_BREAKPOINT_TYPE);
      getBreakpointTypes().unregisterExtension(MY_SIMPLE_BREAKPOINT_TYPE);
    }
    finally {
      super.tearDown();
    }
  }

  public static class MyLineBreakpointType extends XLineBreakpointType<MyBreakpointProperties> {
    public MyLineBreakpointType() {
      super("testLine", "239");
    }

    @Override
    public MyBreakpointProperties createBreakpointProperties(@NotNull final VirtualFile file, final int line) {
      return null;
    }

    @Override
    public MyBreakpointProperties createProperties() {
      return new MyBreakpointProperties();
    }
  }

  public static class MySimpleBreakpointType extends XBreakpointType<XBreakpoint<MyBreakpointProperties>,MyBreakpointProperties> {
    public MySimpleBreakpointType() {
      super("test", "239");
    }

    @Override
    public String getDisplayText(final XBreakpoint<MyBreakpointProperties> breakpoint) {
      return "";
    }

    @Override
    public MyBreakpointProperties createProperties() {
      return new MyBreakpointProperties();
    }

    @Override
    public XBreakpoint<MyBreakpointProperties> createDefaultBreakpoint(@NotNull XBreakpointCreator<MyBreakpointProperties> creator) {
      final XBreakpoint<MyBreakpointProperties> breakpoint = creator.createBreakpoint(new MyBreakpointProperties("default"));
      breakpoint.setEnabled(true);
      return breakpoint;
    }
  }

  protected static class MyBreakpointProperties extends XBreakpointProperties<MyBreakpointProperties> {
    @Attribute("option")
    public String myOption;

    public MyBreakpointProperties() {
    }

    public MyBreakpointProperties(final String option) {
      myOption = option;
    }

    @Override
    public MyBreakpointProperties getState() {
      return this;
    }

    @Override
    public void loadState(final MyBreakpointProperties state) {
      myOption = state.myOption;
    }
  }
}
