/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.TempFiles;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
    myBreakpointManager.loadState(element == null ? new XBreakpointManagerImpl.BreakpointManagerState() : XmlSerializer.deserialize(element, XBreakpointManagerImpl.BreakpointManagerState.class));
  }

  protected Element save() {
    return XmlSerializer.serialize(myBreakpointManager.getState());
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
