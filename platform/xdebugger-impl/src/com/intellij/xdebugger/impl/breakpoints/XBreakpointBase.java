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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.pom.Navigatable;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class XBreakpointBase<Self extends XBreakpoint<P>, P extends XBreakpointProperties, S extends XBreakpointBase.BreakpointState> extends UserDataHolderBase implements XBreakpoint<P> {
  private static final SkipDefaultValuesSerializationFilters SERIALIZATION_FILTERS = new SkipDefaultValuesSerializationFilters();
  private final XBreakpointType<Self, P> myType;
  private final @Nullable P myProperties;
  protected final S myState;
  private final XBreakpointManagerImpl myBreakpointManager;

  public XBreakpointBase(final XBreakpointType<Self, P> type, XBreakpointManagerImpl breakpointManager, final @Nullable P properties, final S state) {
    myState = state;
    myType = type;
    myProperties = properties;
    myBreakpointManager = breakpointManager;
  }

  protected XBreakpointBase(final XBreakpointType<Self, P> type, XBreakpointManagerImpl breakpointManager, S breakpointState) {
    myState = breakpointState;
    myType = type;
    myBreakpointManager = breakpointManager;
    myProperties = type.createProperties();
    if (myProperties != null) {
      //noinspection unchecked
      Object state = XmlSerializer.deserialize(myState.getPropertiesElement(), XDebuggerUtilImpl.getStateClass(myProperties.getClass()));
      //noinspection unchecked
      myProperties.loadState(state);
    }
  }

  protected final Project getProject() {
    return myBreakpointManager.getProject();
  }

  protected XBreakpointManagerImpl getBreakpointManager() {
    return myBreakpointManager;
  }

  protected final void fireBreakpointChanged() {
    myBreakpointManager.fireBreakpointChanged(this);
  }

  public XSourcePosition getSourcePosition() {
    return null;
  }

  public Navigatable getNavigatable() {
    XSourcePosition position = getSourcePosition();
    if (position == null) {
      return null;
    }
    return position.createNavigatable(getProject());
  }

  public boolean isEnabled() {
    return myState.isEnabled();
  }

  public void setEnabled(final boolean enabled) {
    if (enabled != isEnabled()) {
      myState.setEnabled(enabled);
      fireBreakpointChanged();
    }
  }

  @NotNull
  public SuspendPolicy getSuspendPolicy() {
    return myState.mySuspendPolicy;
  }

  public void setSuspendPolicy(@NotNull SuspendPolicy policy) {
    if (myState.mySuspendPolicy != policy) {
      myState.mySuspendPolicy = policy;
      fireBreakpointChanged();
    }
  }

  public boolean isLogMessage() {
    return myState.isLogMessage();
  }

  public void setLogMessage(final boolean logMessage) {
    if (logMessage != isLogMessage()) {
      myState.setLogMessage(logMessage);
      fireBreakpointChanged();
    }
  }

  public String getLogExpression() {
    return myState.getLogExpression();
  }

  public void setLogExpression(@Nullable final String expression) {
    if (!Comparing.equal(getLogExpression(), expression)) {
      myState.setLogExpression(expression);
      fireBreakpointChanged();
    }
  }

  public String getCondition() {
    return myState.getCondition();
  }

  public void setCondition(@Nullable final String condition) {
    if (!Comparing.equal(condition, getCondition())) {
      myState.setCondition(condition);
      fireBreakpointChanged();
    }
  }

  public boolean isValid() {
    return true;
  }

  @Nullable 
  public P getProperties() {
    return myProperties;
  }

  @NotNull
  public XBreakpointType<Self,P> getType() {
    return myType;
  }

  public S getState() {
    Element propertiesElement = myProperties != null ? XmlSerializer.serialize(myProperties.getState(), SERIALIZATION_FILTERS) : null;
    myState.setPropertiesElement(propertiesElement);
    return myState;
  }

  public XBreakpointDependencyState getDependencyState() {
    return myState.getDependencyState();
  }

  public void setDependencyState(XBreakpointDependencyState state) {
    myState.setDependencyState(state);
  }

  public void dispose() {
  }

  @Tag("breakpoint")
  public static class BreakpointState<B extends XBreakpoint<P>, P extends XBreakpointProperties, T extends XBreakpointType<B,P>> {
    private String myTypeId;
    private boolean myEnabled;
    private Element myPropertiesElement;
    private SuspendPolicy mySuspendPolicy = SuspendPolicy.ALL;
    private boolean myLogMessage;
    private String myLogExpression;
    private String myCondition;
    private XBreakpointDependencyState myDependencyState;

    public BreakpointState() {
    }

    public BreakpointState(final boolean enabled, final String typeId) {
      myEnabled = enabled;
      myTypeId = typeId;
    }

    @Attribute("enabled")
    public boolean isEnabled() {
      return myEnabled;
    }

    public void setEnabled(final boolean enabled) {
      myEnabled = enabled;
    }

    @Attribute("type")
    public String getTypeId() {
      return myTypeId;
    }

    public void setTypeId(final String typeId) {
      myTypeId = typeId;
    }

    @Tag("properties")
    public Element getPropertiesElement() {
      return myPropertiesElement;
    }

    public void setPropertiesElement(final Element propertiesElement) {
      myPropertiesElement = propertiesElement;
    }

    @Attribute("suspend")
    public String getSuspendPolicy() {
      return mySuspendPolicy.name();
    }

    public void setSuspendPolicy(final String suspendPolicy) {
      mySuspendPolicy = SuspendPolicy.valueOf(suspendPolicy);
    }

    @Attribute("log-message")
    public boolean isLogMessage() {
      return myLogMessage;
    }

    public void setLogMessage(final boolean logMessage) {
      myLogMessage = logMessage;
    }

    @Tag("log-expression")
    public String getLogExpression() {
      return myLogExpression;
    }

    public void setLogExpression(final String logExpression) {
      myLogExpression = logExpression;
    }

    @Tag("condition")
    public String getCondition() {
      return myCondition;
    }

    public void setCondition(final String condition) {
      myCondition = condition;
    }

    @Property(surroundWithTag = false)
    public XBreakpointDependencyState getDependencyState() {
      return myDependencyState;
    }

    public void setDependencyState(final XBreakpointDependencyState dependencyState) {
      myDependencyState = dependencyState;
    }

    public XBreakpointBase<B,P,?> createBreakpoint(@NotNull T type, @NotNull XBreakpointManagerImpl breakpointManager) {
      return new XBreakpointBase<B, P, BreakpointState<B,P,?>>(type, breakpointManager, this);
    }
  }

  @Override
  public String toString() {
    return "XBreakpointBase(type=" + myType + ")";
  }
}
