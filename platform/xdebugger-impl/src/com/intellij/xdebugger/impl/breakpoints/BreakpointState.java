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
import kotlinx.coroutines.flow.MutableStateFlow;
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
  private final MutableStateFlow<Boolean> myEnabledFlow = createMutableStateFlow(false);
  private Element myPropertiesElement;
  private final MutableStateFlow<SuspendPolicy> mySuspendPolicyFlow = createMutableStateFlow(SuspendPolicy.ALL);
  private final MutableStateFlow<Boolean> myLogMessageFlow = createMutableStateFlow(false);
  private final MutableStateFlow<Boolean> myLogStackFlow = createMutableStateFlow(false);
  private final MutableStateFlow<LogExpression> myLogExpressionFlow = createMutableStateFlow(null);
  private final MutableStateFlow<Condition> myConditionFlow = createMutableStateFlow(null);
  private XBreakpointDependencyState myDependencyState;
  @Tag("group")
  private final MutableStateFlow<String> myGroupFlow = createMutableStateFlow(null);

  @Tag("description")
  private final MutableStateFlow<String> myDescriptionFlow = createMutableStateFlow(null);

  private long myTimeStamp;

  public BreakpointState() {
  }

  public BreakpointState(final boolean enabled, final String typeId, final long timeStamp, final SuspendPolicy suspendPolicy) {
    myEnabledFlow.setValue(enabled);
    myTypeId = typeId;
    myTimeStamp = timeStamp;
    mySuspendPolicyFlow.setValue(suspendPolicy);
  }

  @Attribute("enabled")
  public boolean isEnabled() {
    return myEnabledFlow.getValue();
  }

  public void setEnabled(final boolean enabled) {
    myEnabledFlow.setValue(enabled);
  }

  @Transient
  public StateFlow<Boolean> getEnabledFlow() {
    return myEnabledFlow;
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
    return mySuspendPolicyFlow.getValue().name();
  }

  public void setSuspendPolicyString(final String suspendPolicy) {
    mySuspendPolicyFlow.setValue(SuspendPolicy.valueOf(suspendPolicy));
  }

  @Transient
  public SuspendPolicy getSuspendPolicy() {
    return mySuspendPolicyFlow.getValue();
  }

  public void setSuspendPolicy(SuspendPolicy suspendPolicy) {
    mySuspendPolicyFlow.setValue(suspendPolicy);
  }

  @Transient
  public StateFlow<SuspendPolicy> getSuspendPolicyFlow() {
    return mySuspendPolicyFlow;
  }

  @Attribute("log-message")
  public boolean isLogMessage() {
    return myLogMessageFlow.getValue();
  }

  public void setLogMessage(final boolean logMessage) {
    myLogMessageFlow.setValue(logMessage);
  }

  @Transient
  public StateFlow<Boolean> getLogMessageFlow() {
    return myLogMessageFlow;
  }

  @Attribute("log-stack")
  public boolean isLogStack() {
    return myLogStackFlow.getValue();
  }

  public void setLogStack(final boolean logStack) {
    myLogStackFlow.setValue(logStack);
  }

  @Transient
  public StateFlow<Boolean> getLogStackFlow() {
    return myLogStackFlow;
  }

  public @Nullable String getGroup() {
    return myGroupFlow.getValue();
  }

  public void setGroup(String group) {
    myGroupFlow.setValue(group);
  }

  @Transient
  public StateFlow<String> getGroupFlow() {
    return myGroupFlow;
  }

  public String getDescription() {
    return myDescriptionFlow.getValue();
  }

  public void setDescription(String description) {
    myDescriptionFlow.setValue(description);
  }

  @Transient
  public StateFlow<String> getDescriptionFlow() {
    return myDescriptionFlow;
  }

  @Property(surroundWithTag = false)
  public @Nullable LogExpression getLogExpression() {
    return myLogExpressionFlow.getValue();
  }

  public void setLogExpression(@Nullable LogExpression logExpression) {
    if (logExpression != null) {
      logExpression.checkConverted();
    }
    myLogExpressionFlow.setValue(logExpression);
  }

  @Transient
  public StateFlow<LogExpression> getLogExpressionFlow() {
    return myLogExpressionFlow;
  }

  @Property(surroundWithTag = false)
  public @Nullable Condition getCondition() {
    return myConditionFlow.getValue();
  }

  public void setCondition(@Nullable Condition condition) {
    if (condition != null) {
      condition.checkConverted();
    }
    myConditionFlow.setValue(condition);
  }

  @Transient
  public StateFlow<Condition> getConditionFlow() {
    return myConditionFlow;
  }

  public boolean isLogExpressionEnabled() {
    LogExpression logExpression = myLogExpressionFlow.getValue();
    return logExpression == null || !logExpression.myDisabled;
  }

  public boolean isConditionEnabled() {
    Condition condition = myConditionFlow.getValue();
    return condition == null || !condition.myDisabled;
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
    state.mySuspendPolicyFlow.setValue(mySuspendPolicyFlow.getValue());
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
