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
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

/**
 * @author nik
*/
@Tag("dependency")
public class XBreakpointDependencyState {
  private String myId;
  private String myMasterBreakpointId;
  private boolean myLeaveEnabled = false;

  public XBreakpointDependencyState() {
  }

  public XBreakpointDependencyState(final String id) {
    myId = id;
  }

  public XBreakpointDependencyState(final String id, final String masterBreakpointId, final boolean leaveEnabled) {
    myId = id;
    myMasterBreakpointId = masterBreakpointId;
    myLeaveEnabled = leaveEnabled;
  }

  @Attribute("id")
  public String getId() {
    return myId;
  }

  @Attribute("master-id")
  public String getMasterBreakpointId() {
    return myMasterBreakpointId;
  }

  @Attribute("leave-enabled")
  public boolean isLeaveEnabled() {
    return myLeaveEnabled;
  }

  public void setId(final String id) {
    myId = id;
  }

  public void setMasterBreakpointId(final String masterBreakpointId) {
    myMasterBreakpointId = masterBreakpointId;
  }

  public void setLeaveEnabled(final boolean leaveEnabled) {
    myLeaveEnabled = leaveEnabled;
  }
}
