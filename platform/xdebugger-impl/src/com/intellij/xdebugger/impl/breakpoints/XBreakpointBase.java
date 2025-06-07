// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.JBColor;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.rpc.XBreakpointId;
import com.intellij.xml.CommonXmlStrings;
import com.intellij.xml.util.XmlStringUtil;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.MutableSharedFlow;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

import static com.intellij.platform.util.coroutines.CoroutineScopeKt.childScope;
import static com.intellij.xdebugger.impl.CoroutineUtilsKt.createMutableSharedFlow;
import static com.intellij.xdebugger.impl.breakpoints.XBreakpointProxyKt.asProxy;
import static com.intellij.xdebugger.impl.rpc.models.XBreakpointValueIdKt.storeGlobally;
import static kotlinx.coroutines.CoroutineScopeKt.cancel;

@ApiStatus.Internal
public class XBreakpointBase<Self extends XBreakpoint<P>, P extends XBreakpointProperties, S extends BreakpointState>
  extends UserDataHolderBase implements XBreakpoint<P>, Comparable<Self> {

  private static final @NonNls String BR_NBSP = "<br>" + CommonXmlStrings.NBSP;
  private final XBreakpointType<Self, P> myType;
  private final @Nullable P myProperties;
  protected final S myState;
  private final XBreakpointManagerImpl myBreakpointManager;
  private final CoroutineScope myCoroutineScope;
  private final XBreakpointId myId;
  private Icon myIcon;
  private CustomizedBreakpointPresentation myCustomizedPresentation;
  private boolean myConditionEnabled = true;
  private XExpression myCondition;
  private boolean myLogExpressionEnabled = true;
  private XExpression myLogExpression;
  private volatile boolean myDisposed;
  private final MutableSharedFlow<Unit> myBreakpointChangedFlow = createMutableSharedFlow(0, 1);

  public XBreakpointBase(final XBreakpointType<Self, P> type,
                         XBreakpointManagerImpl breakpointManager,
                         final @Nullable P properties,
                         final S state) {
    myState = state;
    myType = type;
    myProperties = properties;
    myBreakpointManager = breakpointManager;
    myCoroutineScope = childScope(breakpointManager.getCoroutineScope(), "XBreakpoint", EmptyCoroutineContext.INSTANCE, true);
    myId = storeGlobally(this, myCoroutineScope);
    initExpressions();
  }

  private void initExpressions() {
    myConditionEnabled = myState.isConditionEnabled();
    BreakpointState.Condition condition = myState.getCondition();
    myCondition = condition != null ? condition.toXExpression() : null;
    myLogExpressionEnabled = myState.isLogExpressionEnabled();
    BreakpointState.LogExpression expression = myState.getLogExpression();
    myLogExpression = expression != null ? expression.toXExpression() : null;
  }

  public final Project getProject() {
    return myBreakpointManager.getProject();
  }

  protected XBreakpointManagerImpl getBreakpointManager() {
    return myBreakpointManager;
  }

  @ApiStatus.Internal
  public @NotNull CoroutineScope getCoroutineScope() {
    return myCoroutineScope;
  }

  @ApiStatus.Internal
  public @NotNull XBreakpointId getBreakpointId() {
    return myId;
  }

  public final void fireBreakpointChanged() {
    clearIcon();
    myBreakpointManager.fireBreakpointChanged(this);
    myBreakpointChangedFlow.tryEmit(Unit.INSTANCE);
  }

  @ApiStatus.Internal
  public final void fireBreakpointPresentationUpdated(@Nullable XDebugSession session) {
    clearIcon();
    myBreakpointManager.fireBreakpointPresentationUpdated(this, session);
    myBreakpointChangedFlow.tryEmit(Unit.INSTANCE);
  }

  public final Flow<Unit> breakpointChangedFlow() {
    return myBreakpointChangedFlow;
  }

  @Override
  public XSourcePosition getSourcePosition() {
    return getType().getSourcePosition(this);
  }

  @Override
  public Navigatable getNavigatable() {
    XSourcePosition position = getSourcePosition();
    if (position == null) {
      return null;
    }
    return position.createNavigatable(getProject());
  }

  @Override
  public boolean isEnabled() {
    return myState.isEnabled();
  }

  @Override
  public void setEnabled(final boolean enabled) {
    if (enabled != isEnabled()) {
      myState.setEnabled(enabled);
      fireBreakpointChanged();
    }
  }

  @Override
  public @NotNull SuspendPolicy getSuspendPolicy() {
    return myState.getSuspendPolicy();
  }

  @Override
  public void setSuspendPolicy(@NotNull SuspendPolicy policy) {
    if (myState.getSuspendPolicy() != policy) {
      myState.setSuspendPolicy(policy);
      if (policy == SuspendPolicy.NONE) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("debugger.breakpoint.non.suspending");
      }
      fireBreakpointChanged();
    }
  }

  @Override
  public boolean isLogMessage() {
    return myState.isLogMessage();
  }

  @Override
  public void setLogMessage(final boolean logMessage) {
    if (logMessage != isLogMessage()) {
      myState.setLogMessage(logMessage);
      fireBreakpointChanged();
    }
  }

  @Override
  public boolean isLogStack() {
    return myState.isLogStack();
  }

  @Override
  public void setLogStack(final boolean logStack) {
    if (logStack != isLogStack()) {
      myState.setLogStack(logStack);
      fireBreakpointChanged();
    }
  }

  public boolean isConditionEnabled() {
    return myConditionEnabled;
  }

  public void setConditionEnabled(boolean conditionEnabled) {
    if (myConditionEnabled != conditionEnabled) {
      myConditionEnabled = conditionEnabled;
      fireBreakpointChanged();
    }
  }

  public boolean isLogExpressionEnabled() {
    return myLogExpressionEnabled;
  }

  public void setLogExpressionEnabled(boolean logExpressionEnabled) {
    if (myLogExpressionEnabled != logExpressionEnabled) {
      myLogExpressionEnabled = logExpressionEnabled;
      fireBreakpointChanged();
    }
  }

  public String getLogExpression() {
    XExpression expression = getLogExpressionObject();
    return expression != null ? expression.getExpression() : null;
  }

  @Override
  public void setLogExpression(final @Nullable String expression) {
    if (!Objects.equals(getLogExpression(), expression)) {
      myLogExpression = XExpressionImpl.fromText(expression);
      fireBreakpointChanged();
    }
  }

  public XExpression getLogExpressionObjectInt() {
    return myLogExpression;
  }

  @Override
  public @Nullable XExpression getLogExpressionObject() {
    return myLogExpressionEnabled ? myLogExpression : null;
  }

  @Override
  public void setLogExpressionObject(@Nullable XExpression expression) {
    if (!Comparing.equal(myLogExpression, expression)) {
      myLogExpression = expression;
      fireBreakpointChanged();
    }
  }

  public String getCondition() {
    XExpression expression = getConditionExpression();
    return expression != null ? expression.getExpression() : null;
  }

  @Override
  public void setCondition(final @Nullable String condition) {
    if (!Objects.equals(condition, getCondition())) {
      myCondition = XExpressionImpl.fromText(condition);
      fireBreakpointChanged();
    }
  }

  public XExpression getConditionExpressionInt() {
    return myCondition;
  }

  @Override
  public @Nullable XExpression getConditionExpression() {
    return myConditionEnabled ? myCondition : null;
  }

  @Override
  public void setConditionExpression(@Nullable XExpression condition) {
    if (!Comparing.equal(condition, myCondition)) {
      myCondition = condition;
      fireBreakpointChanged();
    }
  }

  @Override
  public long getTimeStamp() {
    return myState.getTimeStamp();
  }

  /**
   * @deprecated Do not use, this is a part of a breakpoint representation.
   */
  @Deprecated
  public boolean isValid() {
    return true;
  }

  @Override
  public @Nullable P getProperties() {
    return myProperties;
  }

  @Override
  public @NotNull XBreakpointType<Self, P> getType() {
    return myType;
  }

  public S getState() {
    Object propertiesState = myProperties == null ? null : myProperties.getState();
    Element element = propertiesState == null ? null : XmlSerializer.serialize(propertiesState);
    Element propertiesElement = element == null ? null : JDOMUtil.internElement(element);
    myState.setCondition(BreakpointState.Condition.create(!myConditionEnabled, myCondition));
    myState.setLogExpression(BreakpointState.LogExpression.create(!myLogExpressionEnabled, myLogExpression));
    myState.setPropertiesElement(propertiesElement);
    return myState;
  }

  public XBreakpointDependencyState getDependencyState() {
    return myState.getDependencyState();
  }

  public void setDependencyState(XBreakpointDependencyState state) {
    if (myState.getDependencyState() != state) {
      myState.setDependencyState(state);
      fireBreakpointChanged();
    }
  }

  public @Nullable String getGroup() {
    return myState.getGroup();
  }

  public void setGroup(String group) {
    String newGroup = StringUtil.nullize(group);
    if (!Objects.equals(newGroup, myState.getGroup())) {
      myState.setGroup(newGroup);
      fireBreakpointChanged();
    }
  }

  public @NlsSafe String getUserDescription() {
    return myState.getDescription();
  }

  public void setUserDescription(String description) {
    String newDescription = StringUtil.nullize(description);
    if (!Objects.equals(newDescription, myState.getDescription())) {
      myState.setDescription(newDescription);
      fireBreakpointChanged();
    }
  }

  public final void dispose() {
    myDisposed = true;
    cancel(myCoroutineScope, null);
    doDispose();
  }

  protected void doDispose() {
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public String toString() {
    return "XBreakpointBase(id = " + myId + ", type=" + myType + ")";
  }

  protected List<? extends AnAction> getAdditionalPopupMenuActions(XDebugSession session) {
    return getType().getAdditionalPopupMenuActions((Self)this, session);
  }

  private static class LineSeparator {
    private final StringBuilder myBuilder;
    private final int myEmptyLength;
    private final String mySeparator;
    private boolean myGetSeparator;

    private LineSeparator(@NotNull StringBuilder builder) {
      myBuilder = builder;
      myEmptyLength = builder.length();
      mySeparator = ExperimentalUI.isNewUI() && !ApplicationManager.getApplication().isUnitTestMode() ? "<br>" : BR_NBSP;
    }

    public @NonNls String get() {
      if (myGetSeparator) {
        return mySeparator;
      }
      myGetSeparator = true;
      return myBuilder.length() > myEmptyLength ? mySeparator : "";
    }
  }

  /**
   * Full description of the breakpoint,
   * including kind of breakpoint target (e.g., "Lambda breakpoint", see {@link XBreakpointUtil#getGeneralDescription(XBreakpoint)})
   * and its properties (e.g., condition).
   * Formatted as HTML document.
   * Primarily used for tooltip in the editor.
   */
  public @NotNull @Nls String getDescription() {
    StringBuilder builder = new StringBuilder();
    builder.append(CommonXmlStrings.HTML_START).append(CommonXmlStrings.BODY_START);
    LineSeparator separator = new LineSeparator(builder);
    builder.append(StringUtil.escapeXmlEntities(XBreakpointUtil.getGeneralDescription(this)));
    var prePropertiesLen = builder.length();

    String errorMessage = getErrorMessage();
    if (!StringUtil.isEmpty(errorMessage)) {
      builder.append(separator.get());
      builder.append("<font color='#").append(ColorUtil.toHex(JBColor.RED)).append("'>");
      builder.append(errorMessage);
      builder.append("</font>");
    }

    var suspendPolicy = getSuspendPolicy();
    if (suspendPolicy == SuspendPolicy.NONE) {
      builder.append(separator.get()).append(XDebuggerBundle.message("xbreakpoint.tooltip.suspend.policy.none"));
    }
    else if (getType().isSuspendThreadSupported()) {
      var defaultSuspendPolicy = myBreakpointManager.getBreakpointDefaults(getType()).getSuspendPolicy();
      if (suspendPolicy != defaultSuspendPolicy) {
        builder.append(separator.get());
        switch (suspendPolicy) {
          case ALL -> builder.append(XDebuggerBundle.message("xbreakpoint.tooltip.suspend.policy.all"));
          case THREAD -> builder.append(XDebuggerBundle.message("xbreakpoint.tooltip.suspend.policy.thread"));
        }
      }
    }

    String condition = getCondition();
    if (!StringUtil.isEmpty(condition)) {
      builder.append(separator.get());
      builder.append(XDebuggerBundle.message("xbreakpoint.tooltip.condition"));
      builder.append(CommonXmlStrings.NBSP);
      builder.append(XmlStringUtil.escapeString(condition));
    }

    if (isLogMessage()) {
      builder.append(separator.get()).append(XDebuggerBundle.message("xbreakpoint.tooltip.log.message"));
    }

    if (isLogStack()) {
      builder.append(separator.get()).append(XDebuggerBundle.message("xbreakpoint.tooltip.log.stack"));
    }

    String logExpression = getLogExpression();
    if (!StringUtil.isEmpty(logExpression)) {
      builder.append(separator.get());
      builder.append(XDebuggerBundle.message("xbreakpoint.tooltip.log.expression"));
      builder.append(CommonXmlStrings.NBSP);
      builder.append(XmlStringUtil.escapeString(logExpression));
    }

    XBreakpoint<?> masterBreakpoint = getBreakpointManager().getDependentBreakpointManager().getMasterBreakpoint(this);
    if (masterBreakpoint != null) {
      builder.append(separator.get());
      String str = XDebuggerBundle.message("xbreakpoint.tooltip.depends.on");
      builder.append(str);
      builder.append(CommonXmlStrings.NBSP);
      builder.append(XBreakpointUtil.getShortText(masterBreakpoint));
    }

    for (@Nls String line : XBreakpointUtil.getPropertyXMLDescriptions(this)) {
      builder.append(separator.get());
      builder.append(line);
    }

    if (prePropertiesLen == builder.length()) {
      builder.append(separator.get());
      builder.append(XDebuggerBundle.message("xbreakpoint.tooltip.edit.hint"));
    }

    builder.append(CommonXmlStrings.BODY_END).append(CommonXmlStrings.HTML_END);
    //noinspection HardCodedStringLiteral
    return builder.toString();
  }

  protected void updateIcon() {
    myIcon = calculateIcon(asProxy(this));
  }

  @ApiStatus.Internal
  public static @NotNull Icon calculateIcon(@NotNull XBreakpointProxy breakpoint) {
    Icon specialIcon = calculateSpecialIcon(breakpoint);
    Icon icon = specialIcon != null ? specialIcon : breakpoint.getType().getEnabledIcon();
    return withQuestionBadgeIfNeeded(icon, breakpoint);
  }

  private static @NotNull Icon withQuestionBadgeIfNeeded(
    @NotNull Icon icon,
    @NotNull XBreakpointProxy breakpoint
  ) {
    if (XDebuggerUtilImpl.isEmptyExpression(breakpoint.getConditionExpression())) {
      return icon;
    }

    LayeredIcon newIcon = new LayeredIcon(2);
    newIcon.setIcon(icon, 0);
    int hShift = ExperimentalUI.isNewUI() ? 7 : 10;
    newIcon.setIcon(AllIcons.Debugger.Question_badge, 1, hShift, 6);
    return JBUIScale.scaleIcon(newIcon);
  }

  private static @Nullable Icon calculateSpecialIcon(
    @NotNull XBreakpointProxy breakpoint
  ) {
    @NotNull XBreakpointTypeProxy type = breakpoint.getType();
    XDebugManagerProxy debugManager = XDebugManagerProxy.getInstance();
    XDebugSessionProxy session = debugManager.getCurrentSessionProxy(breakpoint.getProject());
    XBreakpointManagerProxy breakpointManager = debugManager.getBreakpointManagerProxy(breakpoint.getProject());

    if (!breakpoint.isEnabled()) {
      if (session != null && session.areBreakpointsMuted()) {
        return type.getMutedDisabledIcon();
      }
      else {
        return type.getDisabledIcon();
      }
    }

    if (session == null) {
      if (breakpointManager.getDependentBreakpointManager().getMasterBreakpoint(breakpoint) != null) {
        return type.getInactiveDependentIcon();
      }
    }
    else {
      if (session.areBreakpointsMuted()) {
        return type.getMutedEnabledIcon();
      }
      if (session.isInactiveSlaveBreakpoint(breakpoint)) {
        return type.getInactiveDependentIcon();
      }
      CustomizedBreakpointPresentation presentation = breakpoint.getCustomizedPresentationForCurrentSession();
      if (presentation != null) {
        Icon icon = presentation.getIcon();
        if (icon != null) {
          return icon;
        }
      }
    }

    if (breakpoint.getSuspendPolicy() == SuspendPolicy.NONE) {
      return type.getSuspendNoneIcon();
    }

    CustomizedBreakpointPresentation presentation = breakpoint.getCustomizedPresentation();
    if (presentation != null) {
      final Icon icon = presentation.getIcon();
      if (icon != null) {
        return icon;
      }
    }

    if (
      (breakpoint instanceof XLineBreakpointProxy lineBreakpoint) &&
      lineBreakpoint.isTemporary() &&
      lineBreakpoint.getType().getTemporaryIcon() != null
    ) {
      return lineBreakpoint.getType().getTemporaryIcon();
    }

    return null;
  }

  public Icon getIcon() {
    if (myIcon == null) {
      updateIcon();
    }
    return myIcon;
  }

  public @Nullable String getErrorMessage() {
    final XDebugSessionImpl currentSession = getBreakpointManager().getDebuggerManager().getCurrentSession();
    if (currentSession != null) {
      CustomizedBreakpointPresentation presentation = currentSession.getBreakpointPresentation(this);
      if (presentation != null) {
        final String message = presentation.getErrorMessage();
        if (message != null) return message;
      }
    }
    return myCustomizedPresentation != null ? myCustomizedPresentation.getErrorMessage() : null;
  }

  @ApiStatus.Internal
  public CustomizedBreakpointPresentation getCustomizedPresentation() {
    return myCustomizedPresentation;
  }

  public void setCustomizedPresentation(CustomizedBreakpointPresentation presentation) {
    myCustomizedPresentation = presentation;
    // Don't call fireBreakpointChanged() here, since it should be queued outside
    // See XBreakpointManagerImpl.updateBreakpointPresentation()
  }

  // TODO IJPL-185322
  public @NotNull GutterIconRenderer createGutterIconRenderer() {
    return new BreakpointGutterIconRenderer(asProxy(this));
  }

  public void clearIcon() {
    myIcon = null;
  }

  @Override
  public int compareTo(@NotNull Self self) {
    return myType.getBreakpointComparator().compare((Self)this, self);
  }
}
