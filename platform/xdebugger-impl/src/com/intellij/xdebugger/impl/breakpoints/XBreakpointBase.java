// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.GutterMarkPreprocessor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.GutterDraggableObject;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.JBColor;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
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
import com.intellij.xdebugger.impl.actions.EditBreakpointAction;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsDialogFactory;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.intellij.platform.util.coroutines.CoroutineScopeKt.childScope;
import static com.intellij.xdebugger.XDebuggerUtil.INLINE_BREAKPOINTS_KEY;
import static com.intellij.xdebugger.impl.CoroutineUtilsKt.createMutableSharedFlow;
import static com.intellij.xdebugger.impl.rpc.models.XBreakpointValueIdKt.storeGlobally;
import static kotlinx.coroutines.CoroutineScopeKt.cancel;

@ApiStatus.Internal
public class XBreakpointBase<Self extends XBreakpoint<P>, P extends XBreakpointProperties, S extends BreakpointState> extends UserDataHolderBase implements XBreakpoint<P>, Comparable<Self> {
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

  public XBreakpointBase(final XBreakpointType<Self, P> type, XBreakpointManagerImpl breakpointManager, final @Nullable P properties, final S state) {
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

  public boolean isValid() {
    return true;
  }

  @Override
  public @Nullable P getProperties() {
    return myProperties;
  }

  @Override
  public @NotNull XBreakpointType<Self,P> getType() {
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
    return "XBreakpointBase(type=" + myType + ")";
  }

  protected @Nullable GutterDraggableObject createBreakpointDraggableObject() {
    return null;
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
    final Icon icon = calculateSpecialIcon();
    setIcon(icon != null ? icon : getType().getEnabledIcon());
  }

  protected void setIcon(Icon icon) {
    if (!XDebuggerUtilImpl.isEmptyExpression(getConditionExpression())) {
      LayeredIcon newIcon = new LayeredIcon(2);
      newIcon.setIcon(icon, 0);
      int hShift = ExperimentalUI.isNewUI() ? 7 : 10;
      newIcon.setIcon(AllIcons.Debugger.Question_badge, 1, hShift, 6);
      myIcon = JBUIScale.scaleIcon(newIcon);
    }
    else {
      myIcon = icon;
    }
  }

  protected final @Nullable Icon calculateSpecialIcon() {
    XDebugSessionImpl session = getBreakpointManager().getDebuggerManager().getCurrentSession();
    if (!isEnabled()) {
      if (session != null && session.areBreakpointsMuted()) {
        return getType().getMutedDisabledIcon();
      }
      else {
        return getType().getDisabledIcon();
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

    if (getSuspendPolicy() == SuspendPolicy.NONE) {
      return getType().getSuspendNoneIcon();
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

  CustomizedBreakpointPresentation getCustomizedPresentation() {
    return myCustomizedPresentation;
  }

  public void setCustomizedPresentation(CustomizedBreakpointPresentation presentation) {
    myCustomizedPresentation = presentation;
    // Don't call fireBreakpointChanged() here, since it should be queued outside
    // See XBreakpointManagerImpl.updateBreakpointPresentation()
  }

  public @NotNull GutterIconRenderer createGutterIconRenderer() {
    return new BreakpointGutterIconRenderer();
  }

  public void clearIcon() {
    myIcon = null;
  }

  @Override
  public int compareTo(@NotNull Self self) {
    return myType.getBreakpointComparator().compare((Self)this, self);
  }

  protected abstract static class CommonBreakpointGutterIconRenderer extends GutterIconRenderer {
    @Override
    public @NotNull Alignment getAlignment() {
      return ExperimentalUI.isNewUI() && EditorUtil.isBreakPointsOnLineNumbers() ? Alignment.LINE_NUMBERS : Alignment.RIGHT;
    }
  }

  protected class BreakpointGutterIconRenderer extends CommonBreakpointGutterIconRenderer implements DumbAware {
    @Override
    public @NotNull Icon getIcon() {
      return XBreakpointBase.this.getIcon();
    }

    @Override
    public @NotNull String getAccessibleName() {
      // [tav] todo: add "hit" state
      return XDebuggerBundle.message("accessible.name.icon.0.1.2", getType().getTitle(),
                                     getCondition() != null ? " " + XDebuggerBundle.message("accessible.name.icon.conditional") : "",
                                     !isEnabled() ? " " + XDebuggerBundle.message("accessible.name.icon.disabled") : "");
    }

    @Override
    public @Nullable AnAction getClickAction() {
      if (Registry.is("debugger.click.disable.breakpoints")) {
        return new ToggleBreakpointGutterIconAction(XBreakpointBase.this);
      } else {
        return new RemoveBreakpointGutterIconAction(XBreakpointBase.this);
      }
    }

    @Override
    public @Nullable AnAction getMiddleButtonClickAction() {
      if (!Registry.is("debugger.click.disable.breakpoints")) {
        return new ToggleBreakpointGutterIconAction(XBreakpointBase.this);
      } else {
        return new RemoveBreakpointGutterIconAction(XBreakpointBase.this);
      }
    }

    @Override
    public @Nullable AnAction getRightButtonClickAction() {
      return new EditBreakpointAction.ContextAction(this, XBreakpointBase.this);
    }

    @Override
    public @Nullable ActionGroup getPopupMenuActions() {
      return new DefaultActionGroup(getAdditionalPopupMenuActions(getBreakpointManager().getDebuggerManager().getCurrentSession()));
    }

    @Override
    public @Nullable String getTooltipText() {
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

  protected static class MultipleBreakpointGutterIconRenderer extends CommonBreakpointGutterIconRenderer implements DumbAware {

    private final List<XBreakpointBase<?, ?, ?>> breakpoints;

    public MultipleBreakpointGutterIconRenderer(List<XBreakpointBase<?, ?, ?>> breakpoints) {
      this.breakpoints = breakpoints;
      assert breakpoints.size() >= 2;
    }

    private boolean areAllDisabled() {
      return ContainerUtil.and(breakpoints, b -> !b.isEnabled());
    }

    @Override
    public @NotNull Icon getIcon() {
      var session = breakpoints.get(0).getBreakpointManager().getDebuggerManager().getCurrentSession();
      if (session != null && session.areBreakpointsMuted()) {
        return AllIcons.Debugger.MultipleBreakpointsMuted;
      } else if (areAllDisabled()) {
        return AllIcons.Debugger.MultipleBreakpointsDisabled;
      } else {
        return AllIcons.Debugger.MultipleBreakpoints;
      }
    }

    @Override
    public @NotNull String getAccessibleName() {
      return super.getAccessibleName();
    }

    private AnAction createToggleAction() {
      // This gutter's actions are not collected to any menu, so we use SimpleAction.
      return DumbAwareAction.create(e -> {
        // Semantics:
        // - disable all if any is enabled,
        // - enable all if all are disabled.
        var newEnabledValue = areAllDisabled();
        for (var b : breakpoints) {
          b.setEnabled(newEnabledValue);
        }
      });
    }

    private AnAction createRemoveAction() {
      // This gutter's actions are not collected to any menu, so we use SimpleAction.
      return DumbAwareAction.create(e -> {
        removeBreakpoints();
      });
    }

    private void removeBreakpoints() {
      XDebuggerUtilImpl.removeBreakpointsWithConfirmation(breakpoints);
    }

    @Override
    public @Nullable AnAction getClickAction() {
      if (Registry.is("debugger.click.disable.breakpoints")) {
        return createToggleAction();
      } else {
        return createRemoveAction();
      }
    }

    @Override
    public @Nullable AnAction getMiddleButtonClickAction() {
      if (!Registry.is("debugger.click.disable.breakpoints")) {
        return createToggleAction();
      } else {
        return createRemoveAction();
      }
    }

    @Override
    public @Nullable AnAction getRightButtonClickAction() {
      // This gutter's actions are not collected to any menu, so we use SimpleAction.
      return DumbAwareAction.create(e -> {
        var project = e.getProject();
        if (project == null) return;
        // Initially we select the newest breakpoint, it's shown above other breakpoints in the dialog.
        @SuppressWarnings("OptionalGetWithoutIsPresent") // there are always at least two breakpoints
        var initialOne = breakpoints.stream().sorted().findFirst().get();
        BreakpointsDialogFactory.getInstance(project).showDialog(initialOne);
      });
    }

    @Override
    public @Nullable ActionGroup getPopupMenuActions() {
      // TODO[inline-bp]: show some menu with the list of all breakpoints with some actions for them (remove, edit, ...)
      // TODO[inline-bp]: alt+enter actions are completely broken for multiple breakpoints:
      //                   all actions are mixed and it's hard to separate them
      //                   and it's non trivial to add batch actions "toggle all", "remove all", ...
      //                   see GutterIntentionMenuContributor.collectActions.
      //                   Moreover it might be a good idea to show breakpoint actions on alt+enter only if cursor is in breakpoint's range
      return super.getPopupMenuActions();
    }

    @Override
    public @Nullable String getTooltipText() {
      return XDebuggerBundle.message("xbreakpoint.tooltip.multiple");
    }

    @Override
    public GutterDraggableObject getDraggableObject() {
      return new GutterDraggableObject() {
        @Override
        public boolean copy(int line, VirtualFile file, int actionId) {
          return false; // It's too hard, no copying, please.
        }

        @Override
        public void remove() {
          removeBreakpoints();
        }
      };
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MultipleBreakpointGutterIconRenderer that
        && this.breakpoints.equals(that.breakpoints);
    }

    @Override
    public int hashCode() {
      return breakpoints.hashCode();
    }

  }

  static final class BreakpointGutterIconMerge implements GutterMarkPreprocessor {
    @Override
    public @NotNull List<GutterMark> processMarkers(@NotNull List<GutterMark> marks) {
      // In general, it seems ok to merge breakpoints because they are drawn one over another in the new UI.
      // But we disable it in the old mode just for ease of regressions debugging.
      if (!Registry.is(INLINE_BREAKPOINTS_KEY)) return marks;

      var breakpointCount = ContainerUtil.count(marks, m -> m instanceof CommonBreakpointGutterIconRenderer);
      if (breakpointCount <= 1) {
        return marks;
      }

      var newMarks = new ArrayList<GutterMark>(marks.size() - breakpointCount + 1);
      var breakpoints = new ArrayList<XBreakpointBase<?, ?, ?>>(breakpointCount);
      var breakpointMarkPosition = -1;
      for (GutterMark mark : marks) {
        assert !(mark instanceof MultipleBreakpointGutterIconRenderer) : "they are not expected to be created before processing";
        if (mark instanceof XBreakpointBase<?,?,?>.BreakpointGutterIconRenderer singleBreakpointMark) {
          breakpoints.add(singleBreakpointMark.getBreakpoint());
          breakpointMarkPosition = newMarks.size();
          continue;
        }

        newMarks.add(mark);
      }
      newMarks.add(breakpointMarkPosition, new MultipleBreakpointGutterIconRenderer(breakpoints));

      return newMarks;
    }
  }
}
