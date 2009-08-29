package com.intellij.xdebugger;

import com.intellij.mock.MockProject;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import org.jdom.Element;

/**
 * @author nik
 */
public abstract class XBreakpointsTestCase extends XDebuggerTestCase {
  protected XBreakpointManagerImpl myBreakpointManager;

  protected void setUp() throws Exception {
    super.setUp();
    MockProject project = disposeOnTearDown(new MockProject());
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
