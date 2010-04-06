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

import com.intellij.mock.MockProject;
import com.intellij.openapi.vfs.impl.http.RemoteFileManager;
import com.intellij.openapi.vfs.impl.http.RemoteFileManagerImpl;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import org.jdom.Element;

/**
 * @author nik
 */
public abstract class XBreakpointsTestCase extends XDebuggerTestCase {
  protected XBreakpointManagerImpl myBreakpointManager;

  protected void setUp() throws Exception {
    super.setUp();
    MockProject project = disposeOnTearDown(new MockProject());
    getApplication().registerService(RemoteFileManager.class, RemoteFileManagerImpl.class);
    myBreakpointManager = new XBreakpointManagerImpl(project, null, null);
  }

  protected void load(final Element element) {
    XBreakpointManagerImpl.BreakpointManagerState managerState = XmlSerializer.deserialize(element, XBreakpointManagerImpl.BreakpointManagerState.class);
    myBreakpointManager.loadState(managerState);
  }

  protected Element save() {
    return XmlSerializer.serialize(myBreakpointManager.getState(), new SkipDefaultValuesSerializationFilters());
  }
}
