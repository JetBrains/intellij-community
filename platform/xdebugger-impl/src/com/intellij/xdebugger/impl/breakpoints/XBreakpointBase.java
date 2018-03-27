/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.configurationStore.ComponentSerializationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterDraggableObject;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerSupport;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.actions.EditBreakpointAction;
import com.intellij.xml.CommonXmlStrings;
import com.intellij.xml.util.XmlStringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class XBreakpointBase<Self extends XBreakpoint<P>, P extends XBreakpointProperties, S extends BreakpointState> extends UserDataHolderBase implements XBreakpoint<P>, Comparable<Self> {
  private static final SkipDefaultValuesSerializationFilters SERIALIZATION_FILTERS = new SkipDefaultValuesSerializationFilters();
  @NonNls private static final String BR_NBSP = "<br>" + CommonXmlStrings.NBSP;
  private final XBreakpointType<Self, P> myType;
  private final @Nullable P myProperties;
  protected final S myState;
  private final XBreakpointManagerImpl myBreakpointManager;
  private Icon myIcon;
  private CustomizedBreakpointPresentation myCustomizedPresentation;
  private boolean myConditionEnabled = true;
  private XExpression myCondition;
  private boolean myLogExpressionEnabled = true;
  private XExpression myLogExpression;
  private volatile boolean myDisposed;

  public XBreakpointBase(final XBreakpointType<Self, P> type, XBreakpointManagerImpl breakpointManager, final @Nullable P properties, final S state) {
    myState = state;
    myType = type;
    myProperties = properties;
    myBreakpointManager = breakpointManager;
    initExpressions();
  }

  protected XBreakpointBase(final XBreakpointType<Self, P> type, XBreakpointManagerImpl breakpointManager, S breakpointState) {
    myState = breakpointState;
    myType = type;
    myBreakpointManager = breakpointManager;
    myProperties = type.createProperties();
    if (myProperties != null) {
      ComponentSerializationUtil.loadComponentState(myProperties, myState.getPropertiesElement());
    }
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

  public final void fireBreakpointChanged() {
    clearIcon();
    myBreakpointManager.fireBreakpointChanged(this);
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
  @NotNull
  public SuspendPolicy getSuspendPolicy() {
    return myState.getSuspendPolicy();
  }

  @Override
  public void setSuspendPolicy(@NotNull SuspendPolicy policy) {
    if (myState.getSuspendPolicy() != policy) {
      myState.setSuspendPolicy(policy);
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

  @Override
  public String getLogExpression() {
    XExpression expression = getLogExpressionObject();
    return expression != null ? expression.getExpression() : null;
  }

  @Override
  public void setLogExpression(@Nullable final String expression) {
    if (!Comparing.equal(getLogExpression(), expression)) {
      myLogExpression = XExpressionImpl.fromText(expression);
      fireBreakpointChanged();
    }
  }

  public XExpression getLogExpressionObjectInt() {
    return myLogExpression;
  }

  @Nullable
  @Override
  public XExpression getLogExpressionObject() {
    return myLogExpressionEnabled ? myLogExpression : null;
  }

  @Override
  public void setLogExpressionObject(@Nullable XExpression expression) {
    if (!Comparing.equal(myLogExpression, expression)) {
      myLogExpression = expression;
      fireBreakpointChanged();
    }
  }

  @Override
  public String getCondition() {
    XExpression expression = getConditionExpression();
    return expression != null ? expression.getExpression() : null;
  }

  @Override
  public void setCondition(@Nullable final String condition) {
    if (!Comparing.equal(condition, getCondition())) {
      myCondition = XExpressionImpl.fromText(condition);
      fireBreakpointChanged();
    }
  }

  public XExpression getConditionExpressionInt() {
    return myCondition;
  }

  @Nullable
  @Override
  public XExpression getConditionExpression() {
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

  public boolean isValid() {
    return true;
  }

  @Override
  @Nullable
  public P getProperties() {
    return myProperties;
  }

  @Override
  @NotNull
  public XBreakpointType<Self,P> getType() {
    return myType;
  }

  public S getState() {
    Element propertiesElement =
      myProperties == null ? null : JDOMUtil.internElement(XmlSerializer.serialize(myProperties.getState(), SERIALIZATION_FILTERS));
    myState.setCondition(BreakpointState.Condition.create(!myConditionEnabled, myCondition));
    myState.setLogExpression(BreakpointState.LogExpression.create(!myLogExpressionEnabled, myLogExpression));
    myState.setPropertiesElement(propertiesElement);
    return myState;
  }

  public XBreakpointDependencyState getDependencyState() {
    return myState.getDependencyState();
  }

  public void setDependencyState(XBreakpointDependencyState state) {
    myState.setDependencyState(state);
  }

  @Nullable
  public String getGroup() {
    return myState.getGroup();
  }

  public void setGroup(String group) {
    myState.setGroup(StringUtil.nullize(group));
  }

  public String getUserDescription() {
    return myState.getDescription();
  }

  public void setUserDescription(String description) {
    myState.setDescription(StringUtil.nullize(description));
  }

  public final void dispose() {
    myDisposed = true;
    doDispose();
  }

  protected void doDispose() {
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public String toString() {
    return "XBreakpointBase(type=" + myType + ")";
  }

  @Nullable
  protected GutterDraggableObject createBreakpointDraggableObject() {
    return null;
  }

  protected List<? extends AnAction> getAdditionalPopupMenuActions(XDebugSession session) {
    return Collections.emptyList();
  }

  @NotNull
  public String getDescription() {
    @NonNls StringBuilder builder = new StringBuilder();
    builder.append(CommonXmlStrings.HTML_START).append(CommonXmlStrings.BODY_START);
    builder.append(XBreakpointUtil.getDisplayText(this));

    String errorMessage = getErrorMessage();
    if (!StringUtil.isEmpty(errorMessage)) {
      builder.append(BR_NBSP);
      builder.append("<font color='#").append(ColorUtil.toHex(JBColor.RED)).append("'>");
      builder.append(errorMessage);
      builder.append("</font>");
    }

    if (getSuspendPolicy() == SuspendPolicy.NONE) {
      builder.append(BR_NBSP).append(XDebuggerBundle.message("xbreakpoint.tooltip.suspend.policy.none"));
    }
    else if (getType().isSuspendThreadSupported()) {
      builder.append(BR_NBSP);
      //noinspection EnumSwitchStatementWhichMissesCases
      switch (getSuspendPolicy()) {
        case ALL:
          builder.append(XDebuggerBundle.message("xbreakpoint.tooltip.suspend.policy.all"));
          break;
        case THREAD:
          builder.append(XDebuggerBundle.message("xbreakpoint.tooltip.suspend.policy.thread"));
          break;
      }
    }

    String condition = getCondition();
    if (!StringUtil.isEmpty(condition)) {
      builder.append(BR_NBSP);
      builder.append(XDebuggerBundle.message("xbreakpoint.tooltip.condition"));
      builder.append(CommonXmlStrings.NBSP);
      builder.append(XmlStringUtil.escapeString(condition));
    }

    if (isLogMessage()) {
      builder.append(BR_NBSP).append(XDebuggerBundle.message("xbreakpoint.tooltip.log.message"));
    }

    if (isLogStack()) {
      builder.append(BR_NBSP).append(XDebuggerBundle.message("xbreakpoint.tooltip.log.stack"));
    }

    String logExpression = getLogExpression();
    if (!StringUtil.isEmpty(logExpression)) {
      builder.append(BR_NBSP);
      builder.append(XDebuggerBundle.message("xbreakpoint.tooltip.log.expression"));
      builder.append(CommonXmlStrings.NBSP);
      builder.append(XmlStringUtil.escapeString(logExpression));
    }

    XBreakpoint<?> masterBreakpoint = getBreakpointManager().getDependentBreakpointManager().getMasterBreakpoint(this);
    if (masterBreakpoint != null) {
      builder.append(BR_NBSP);
      String str = XDebuggerBundle.message("xbreakpoint.tooltip.depends.on");
      builder.append(str);
      builder.append(CommonXmlStrings.NBSP);
      builder.append(XBreakpointUtil.getShortText(masterBreakpoint));
    }

    builder.append(CommonXmlStrings.BODY_END).append(CommonXmlStrings.HTML_END);
    return builder.toString();
  }

  protected void updateIcon() {
    final Icon icon = calculateSpecialIcon();
    setIcon(icon != null ? icon : getType().getEnabledIcon());
  }

  protected void setIcon(Icon icon) {
    if (!XDebuggerUtilImpl.isEmptyExpression(getConditionExpression())) {
      LayeredIcon newIcon = new LayeredIcon(2);
      newIcon.setIcon(icon, 0);
      newIcon.setIcon(AllIcons.Debugger.Question_badge, 1, 10, 6);
      myIcon = JBUI.scale(newIcon);
    }
    else {
      myIcon = icon;
    }
  }

  @Nullable
  protected final Icon calculateSpecialIcon() {
    XDebugSessionImpl session = getBreakpointManager().getDebuggerManager().getCurrentSession();
    if (!isEnabled()) {
      // disabled icon takes precedence to other to visually distinguish it and provide feedback then it is enabled/disabled
      // (e.g. in case of mute-mode we would like to differentiate muted but enabled breakpoints from simply disabled ones)
      if (session == null || !session.areBreakpointsMuted()) {
        return getType().getDisabledIcon();
      }
      else {
        return getType().getMutedDisabledIcon();
      }
    }

    if (session == null) {
      if (getBreakpointManager().getDependentBreakpointManager().getMasterBreakpoint(this) != null) {
        return getType().getInactiveDependentIcon();
      }
    }
    else {
      if (session.areBreakpointsMuted()) {
        return getType().getMutedEnabledIcon();
      }
      if (session.isInactiveSlaveBreakpoint(this)) {
        return getType().getInactiveDependentIcon();
      }
      CustomizedBreakpointPresentation presentation = session.getBreakpointPresentation(this);
      if (presentation != null) {
        Icon icon = presentation.getIcon();
        if (icon != null) {
          return icon;
        }
      }
    }
    if (myCustomizedPresentation != null) {
      final Icon icon = myCustomizedPresentation.getIcon();
      if (icon != null) {
        return icon;
      }
    }
    return null;
  }

  public Icon getIcon() {
    if (myIcon == null) {
      updateIcon();
    }
    return myIcon;
  }

  @Nullable
  public String getErrorMessage() {
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

  CustomizedBreakpointPresentation getCustomizedPresentation() {
    return myCustomizedPresentation;
  }

  public void setCustomizedPresentation(CustomizedBreakpointPresentation presentation) {
    myCustomizedPresentation = presentation;
  }

  @NotNull
  public GutterIconRenderer createGutterIconRenderer() {
    return new BreakpointGutterIconRenderer();
  }

  public void clearIcon() {
    myIcon = null;
  }

  @Override
  public int compareTo(@NotNull Self self) {
    return myType.getBreakpointComparator().compare((Self)this, self);
  }

  protected class BreakpointGutterIconRenderer extends GutterIconRenderer implements DumbAware {
    @Override
    @NotNull
    public Icon getIcon() {
      return XBreakpointBase.this.getIcon();
    }

    @Override
    @Nullable
    public AnAction getClickAction() {
      if (Registry.is("debugger.click.disable.breakpoints")) {
        return new ToggleBreakpointGutterIconAction(XBreakpointBase.this);
      } else {
        return new RemoveBreakpointGutterIconAction(XBreakpointBase.this);
      }
    }

    @Override
    @Nullable
    public AnAction getMiddleButtonClickAction() {
      if (!Registry.is("debugger.click.disable.breakpoints")) {
        return new ToggleBreakpointGutterIconAction(XBreakpointBase.this);
      } else {
        return new RemoveBreakpointGutterIconAction(XBreakpointBase.this);
      }
    }

    @Nullable
    @Override
    public AnAction getRightButtonClickAction() {
      return new EditBreakpointAction.ContextAction(this, XBreakpointBase.this, DebuggerSupport.getDebuggerSupport(XDebuggerSupport.class));
    }

    @NotNull
    @Override
    public Alignment getAlignment() {
      return Alignment.RIGHT;
    }

    @Override
    @Nullable
    public ActionGroup getPopupMenuActions() {
      return null;
    }

    @Override
    @Nullable
    public String getTooltipText() {
      return getDescription();
    }

    @Override
    public GutterDraggableObject getDraggableObject() {
      return createBreakpointDraggableObject();
    }

    private XBreakpointBase<?,?,?> getBreakpoint() {
      return XBreakpointBase.this;
    }
    @Override
    public boolean equals(Object obj) {
      return obj instanceof XLineBreakpointImpl.BreakpointGutterIconRenderer
             && getBreakpoint() == ((XLineBreakpointImpl.BreakpointGutterIconRenderer)obj).getBreakpoint()
             && Comparing.equal(getIcon(), ((XLineBreakpointImpl.BreakpointGutterIconRenderer)obj).getIcon());
    }

    @Override
    public int hashCode() {
      return getBreakpoint().hashCode();
    }
  }
}
