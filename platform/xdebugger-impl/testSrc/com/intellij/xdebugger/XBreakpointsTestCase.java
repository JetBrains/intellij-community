/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.TempFiles;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.impl.BreakpointManagerState;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.configurationStore.XmlSerializer.deserialize;
import static com.intellij.configurationStore.XmlSerializer.serialize;

/**
 * @author nik
 */
public abstract class XBreakpointsTestCase extends XDebuggerTestCase {
  protected XBreakpointManagerImpl myBreakpointManager;
  protected TempFiles myTempFiles;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myBreakpointManager = (XBreakpointManagerImpl)XDebuggerManager.getInstance(myProject).getBreakpointManager();
    myTempFiles = new TempFiles(myFilesToDelete);
  }

  @Override
  protected void tearDown() throws Exception {
    myBreakpointManager = null;
    super.tearDown();
  }

  protected void load(@Nullable Element element) {
    myBreakpointManager.loadState(element == null ? new BreakpointManagerState() : deserialize(element, BreakpointManagerState.class));
  }

  @Nullable
  protected Element save() {
    BreakpointManagerState state = new BreakpointManagerState();
    myBreakpointManager.saveState(state);
    return serialize(state);
  }

  protected List<XBreakpoint<?>> getAllBreakpoints() {
    final XBreakpointBase<?, ?, ?>[] breakpoints =
      ReadAction.compute(() -> myBreakpointManager.getAllBreakpoints());
    final List<XBreakpoint<?>> result = new ArrayList<>();
    for (XBreakpointBase<?, ?, ?> breakpoint : breakpoints) {
      final XBreakpointType type = breakpoint.getType();
      if (type instanceof MySimpleBreakpointType || type instanceof MyLineBreakpointType) {
        result.add(breakpoint);
      }
    }
    result.sort((o1, o2) -> StringUtil.compare(((MyBreakpointProperties)o1.getProperties()).myOption,
                                               ((MyBreakpointProperties)o2.getProperties()).myOption, true));
    return result;
  }
}
