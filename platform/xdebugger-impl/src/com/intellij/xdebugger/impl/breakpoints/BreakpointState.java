// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import kotlinx.coroutines.flow.StateFlow;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.xdebugger.impl.CoroutineUtilsKt.createMutableStateFlow;

@Tag("breakpoint")
@ApiStatus.Internal
public class BreakpointState<B extends XBreakpoint<P>, P extends XBreakpointProperties, T extends XBreakpointType<B,P>> {
  private String myTypeId;
  private boolean myEnabled;
  private Element myPropertiesElement;
  private SuspendPolicy mySuspendPolicy = SuspendPolicy.ALL;
  private boolean myLogMessage;
  private boolean myLogStack;
  private LogExpression myLogExpression;
  private Condition myCondition;
  private XBreakpointDependencyState myDependencyState;
  @Tag("group")
  private String myGroup;

  @Tag("description")
  private String myDescription;

  private long myTimeStamp;

  public BreakpointState() {
  }

  public BreakpointState(final boolean enabled, final String typeId, final long timeStamp, final SuspendPolicy suspendPolicy) {
    myEnabled = enabled;
    myTypeId = typeId;
    myTimeStamp = timeStamp;
    mySuspendPolicy = suspendPolicy;
  }

  @Attribute("enabled")
  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(final boolean enabled) {
    myEnabled = enabled;
  }

  @Transient
  public StateFlow<Boolean> getEnabledFlow() {
    // TODO: implement proper reactivity taking serialization into account
    return createMutableStateFlow(myEnabled);
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
  public String getSuspendPolicyString() {
    return mySuspendPolicy.name();
  }

  public void setSuspendPolicyString(final String suspendPolicy) {
    mySuspendPolicy = SuspendPolicy.valueOf(suspendPolicy);
  }

  @Transient
  public SuspendPolicy getSuspendPolicy() {
    return mySuspendPolicy;
  }

  public void setSuspendPolicy(SuspendPolicy suspendPolicy) {
    mySuspendPolicy = suspendPolicy;
  }

  @Transient
  public StateFlow<SuspendPolicy> getSuspendPolicyFlow() {
    // TODO: implement proper reactivity taking serialization into account
    return createMutableStateFlow(mySuspendPolicy);
  }

  @Attribute("log-message")
  public boolean isLogMessage() {
    return myLogMessage;
  }

  public void setLogMessage(final boolean logMessage) {
    myLogMessage = logMessage;
  }

  @Transient
  public StateFlow<Boolean> getLogMessageFlow() {
    // TODO: implement proper reactivity taking serialization into account
    return createMutableStateFlow(myLogMessage);
  }

  @Attribute("log-stack")
  public boolean isLogStack() {
    return myLogStack;
  }

  public void setLogStack(final boolean logStack) {
    myLogStack = logStack;
  }

  @Transient
  public StateFlow<Boolean> getLogStackFlow() {
    // TODO: implement proper reactivity taking serialization into account
    return createMutableStateFlow(myLogStack);
  }

  public @Nullable String getGroup() {
    return myGroup;
  }

  public void setGroup(String group) {
    myGroup = group;
  }

  @Transient
  public StateFlow<String> getGroupFlow() {
    // TODO: implement proper reactivity taking serialization into account
    return createMutableStateFlow(myGroup);
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  @Transient
  public StateFlow<String> getDescriptionFlow() {
    // TODO: implement proper reactivity taking serialization into account
    return createMutableStateFlow(myDescription);
  }

  @Property(surroundWithTag = false)
  public @Nullable LogExpression getLogExpression() {
    return myLogExpression;
  }

  public void setLogExpression(@Nullable LogExpression logExpression) {
    if (logExpression != null) {
      logExpression.checkConverted();
    }
    myLogExpression = logExpression;
  }

  @Property(surroundWithTag = false)
  public @Nullable Condition getCondition() {
    return myCondition;
  }

  public void setCondition(@Nullable Condition condition) {
    if (condition != null) {
      condition.checkConverted();
    }
    myCondition = condition;
  }

  public boolean isLogExpressionEnabled() {
    return myLogExpression == null || !myLogExpression.myDisabled;
  }

  public boolean isConditionEnabled() {
    return myCondition == null || !myCondition.myDisabled;
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

  public long getTimeStamp() {
    return myTimeStamp;
  }

  public void setTimeStamp(long timeStamp) {
    myTimeStamp = timeStamp;
  }

  void applyDefaults(BreakpointState state) {
    state.mySuspendPolicy = mySuspendPolicy;
  }

  @Tag("condition")
  public static class Condition extends XExpressionState {
    public Condition() {
    }

    private Condition(boolean disabled, XExpression expression) {
      super(disabled, expression);
    }

    public static @Nullable Condition create(boolean disabled, XExpression expression) {
      if (XDebuggerUtilImpl.isEmptyExpression(expression)) {
        return null;
      }
      return new Condition(disabled, expression);
    }
  }

  @Tag("log-expression")
  public static class LogExpression extends XExpressionState {
    public LogExpression() {
    }

    private LogExpression(boolean disabled, XExpression expression) {
      super(disabled, expression);
    }

    public static @Nullable LogExpression create(boolean disabled, XExpression expression) {
      if (XDebuggerUtilImpl.isEmptyExpression(expression)) {
        return null;
      }
      return new LogExpression(disabled, expression);
    }
  }
}
