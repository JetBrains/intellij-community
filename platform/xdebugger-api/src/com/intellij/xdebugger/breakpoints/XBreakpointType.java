// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xdebugger.breakpoints;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Implement this class to support new type of breakpoints. An implementation should be registered in a plugin.xml:
 * <p>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;xdebugger.breakpointType implementation="qualified-class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p><p>
 *
 * Use this class only for breakpoints like exception breakpoints in Java. If a breakpoint will be put on some line in a file use
 * {@link XLineBreakpointType} instead 
 */
public abstract class XBreakpointType<B extends XBreakpoint<P>, P extends XBreakpointProperties> {
  public static final ExtensionPointName<XBreakpointType> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.xdebugger.breakpointType");
  private final @NonNls @NotNull String myId;
  private final @Nls @NotNull String myTitle;
  private final boolean mySuspendThreadSupported;

  /**
   * @param id an unique id of breakpoint type
   * @param title title of tab in the breakpoints dialog
   */
  protected XBreakpointType(final @NonNls @NotNull String id, final @Nls @NotNull String title) {
    this(id, title, false);
  }

  /**
   * @param id                     an unique id of breakpoint type
   * @param title                  title of tab in the breakpoints dialog
   * @param suspendThreadSupported {@code true} if suspending only one thread is supported for this type of breakpoints
   */
  protected XBreakpointType(final @NonNls @NotNull String id, final @Nls @NotNull String title, boolean suspendThreadSupported) {
    myId = id;
    myTitle = title;
    mySuspendThreadSupported = suspendThreadSupported;
  }

  public @Nullable P createProperties() {
    return null;
  }

  /**
   * @return {@code true} if suspending only one thread is supported
   */
  public boolean isSuspendThreadSupported() {
    return mySuspendThreadSupported;
  }

  public SuspendPolicy getDefaultSuspendPolicy() {
    return SuspendPolicy.ALL;
  }

  public enum StandardPanels {SUSPEND_POLICY, ACTIONS, DEPENDENCY}

  public EnumSet<StandardPanels> getVisibleStandardPanels() {
    return EnumSet.allOf(StandardPanels.class);
  }

  public final @NotNull String getId() {
    return myId;
  }

  public @NotNull @Nls String getTitle() {
    return myTitle;
  }

  public @NotNull Icon getEnabledIcon() {
    return AllIcons.Debugger.Db_set_breakpoint;
  }

  public @NotNull Icon getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_breakpoint;
  }

  public @NotNull Icon getSuspendNoneIcon() {
    return AllIcons.Debugger.Db_no_suspend_breakpoint;
  }

  public @NotNull Icon getMutedEnabledIcon() {
    return AllIcons.Debugger.Db_muted_breakpoint;
  }

  public @NotNull Icon getMutedDisabledIcon() {
    return AllIcons.Debugger.Db_muted_disabled_breakpoint;
  }

  /**
   * @return the icon shown for a breakpoint which is scheduled but not yet set (validated, resolved) in the debugger engine
   */
  public @Nullable Icon getPendingIcon() {
    return null;
  }

  /**
   * @return the icon which is shown for a dependent breakpoint until its master breakpoint is reached
   */
  public @NotNull Icon getInactiveDependentIcon() {
    return AllIcons.Debugger.Db_dep_line_breakpoint;
  }

  /**
   * Description of breakpoint target
   * (e.g.,
   * "Line 20 in Hello.foo()" for line breakpoint,
   * "foo.Bar.workHard()" for method breakpoint,
   * ...).
   */
  public abstract @Nls String getDisplayText(B breakpoint);

  /**
   * Laconic breakpoint description with specification of its kind (type of target).
   * Primarily used for tooltip in the editor, when an exact target is obvious but overall semantics might be unclear.
   * E.g.: "Line breakpoint", "Lambda breakpoint", "Field breakpoint".
   *
   * @see XLineBreakpointType#getGeneralDescription(XLineBreakpointType.XLineBreakpointVariant)
   */
  public @Nls String getGeneralDescription(B breakpoint) {
    // Default implementation just for API backward compatibility, it's highly recommended to properly implement this method.
    return getDisplayText(breakpoint);
  }

  /**
   * Description lines of specific breakpoint properties (e.g., class filter for Java line breakpoints),
   * XML formatted.
   */
  public List<@Nls String> getPropertyXMLDescriptions(B breakpoint) {
    return Collections.emptyList();
  }

  public @Nullable XBreakpointCustomPropertiesPanel<B> createCustomConditionsPanel() {
    return null;
  }

  public @Nullable XBreakpointCustomPropertiesPanel<B> createCustomPropertiesPanel(@NotNull Project project) {
    return null;
  }

  public @Nullable XBreakpointCustomPropertiesPanel<B> createCustomRightPropertiesPanel(@NotNull Project project) {
    return null;
  }

  public @Nullable XBreakpointCustomPropertiesPanel<B> createCustomTopPropertiesPanel(@NotNull Project project) {
    return null;
  }

  /**
   * @deprecated override {@link #getEditorsProvider(B, Project)} instead
   */
  @Deprecated
  public @Nullable XDebuggerEditorsProvider getEditorsProvider() {
    return null;
  }

  public @Nullable XDebuggerEditorsProvider getEditorsProvider(@NotNull B breakpoint, @NotNull Project project) {
    return getEditorsProvider();
  }

  public List<XBreakpointGroupingRule<B, ?>> getGroupingRules() {
    return Collections.emptyList();
  }

  public @NotNull Comparator<B> getBreakpointComparator() {
    return (b, b1) -> Long.compare(b1.getTimeStamp(), b.getTimeStamp());
    //return XDebuggerUtil.getInstance().getDefaultBreakpointComparator(this);
  }

  /**
   * Return {@code true} from this method in order to allow adding breakpoints from the "Breakpoints" dialog. Also override
   * {@link XBreakpointType#addBreakpoint(Project,JComponent)} method.
   * @return {@code true} if "Add" button should be visible in "Breakpoints" dialog
   */
  public boolean isAddBreakpointButtonVisible() {
    return false;
  }

  /**
   * This method is called then "Add" button is pressed in the "Breakpoints" dialog 
   * @return the created breakpoint or {@code null} if breakpoint wasn't created
   */
  public @Nullable B addBreakpoint(final Project project, JComponent parentComponent) {
    return null;
  }

  /**
   * Returns properties of the default breakpoint. The default breakpoints cannot be deleted and is always shown on top of the breakpoints
   * list in the dialog.
   *
   * @return a default breakpoint or {@code null} if default breakpoint isn't supported
   */
  public @Nullable XBreakpoint<P> createDefaultBreakpoint(@NotNull XBreakpointCreator<P> creator) {
    return null;
  }

  public boolean shouldShowInBreakpointsDialog(@NotNull Project project) {
    return true;
  }

  public @Nullable @NonNls String getBreakpointsDialogHelpTopic() {
    return null;
  }

  /**
   * Override this method to define source position for a breakpoint. It will be used e.g. by 'Go To' and 'View Source' buttons in 'Breakpoints' dialog
   */
  public @Nullable XSourcePosition getSourcePosition(@NotNull XBreakpoint<P> breakpoint) {
    return null;
  }

  /**
   * Laconic textual description identifying this breakpoint among other ones of the same type.
   * Usually used in the list of breakpoints.
   * It is expected to be short.
   */
  public @Nls String getShortText(B breakpoint) {
    return getDisplayText(breakpoint);
  }

  public interface XBreakpointCreator<P extends XBreakpointProperties> {
    @NotNull
    XBreakpoint<P> createBreakpoint(@Nullable P properties);
  }

  public List<? extends AnAction> getAdditionalPopupMenuActions(@NotNull B breakpoint, @Nullable XDebugSession currentSession) {
    return Collections.emptyList();
  }

  @Override
  public String toString() {
    return myId;
  }
}
