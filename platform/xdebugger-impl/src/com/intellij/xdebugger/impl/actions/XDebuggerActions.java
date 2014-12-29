/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.actions;

import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public interface XDebuggerActions {
  @NonNls String VIEW_BREAKPOINTS = "ViewBreakpoints";

  @NonNls String RESUME = "Resume";
  @NonNls String PAUSE = "Pause";

  @NonNls String STEP_OVER = "StepOver";
  @NonNls String STEP_INTO = "StepInto";
  @NonNls String SMART_STEP_INTO = "SmartStepInto";
  @NonNls String FORCE_STEP_INTO = "ForceStepInto";
  @NonNls String STEP_OUT = "StepOut";

  @NonNls String RUN_TO_CURSOR = "RunToCursor";
  @NonNls String FORCE_RUN_TO_CURSOR = "ForceRunToCursor";
  @NonNls String EDIT_TYPE_SOURCE = "Debugger.EditTypeSource";

  @NonNls String SHOW_EXECUTION_POINT = "ShowExecutionPoint";
  @NonNls String JUMP_TO_SOURCE = "XDebugger.JumpToSource";
  @NonNls String JUMP_TO_TYPE_SOURCE = "XDebugger.JumpToTypeSource";

  @NonNls String EVALUATE_EXPRESSION = "EvaluateExpression";
  @NonNls String QUICK_EVALUATE_EXPRESSION = "QuickEvaluateExpression";

  @NonNls String TOOL_WINDOW_TOP_TOOLBAR_GROUP = "XDebugger.ToolWindow.TopToolbar";
  @NonNls String TOOL_WINDOW_LEFT_TOOLBAR_GROUP = "XDebugger.ToolWindow.LeftToolbar";
  @NonNls String EVALUATE_DIALOG_TREE_POPUP_GROUP = "XDebugger.Evaluation.Dialog.Tree.Popup";
  @NonNls String INSPECT_TREE_POPUP_GROUP = "XDebugger.Inspect.Tree.Popup";
  @NonNls String VARIABLES_TREE_TOOLBAR_GROUP = "XDebugger.Variables.Tree.Toolbar";
  @NonNls String VARIABLES_TREE_POPUP_GROUP = "XDebugger.Variables.Tree.Popup";
  @NonNls String WATCHES_TREE_POPUP_GROUP = "XDebugger.Watches.Tree.Popup";
  @NonNls String WATCHES_TREE_TOOLBAR_GROUP = "XDebugger.Watches.Tree.Toolbar";
  @NonNls String FRAMES_TREE_POPUP_GROUP = "XDebugger.Frames.Tree.Popup";
  @NonNls String FRAMES_TOP_TOOLBAR_GROUP = "XDebugger.Frames.TopToolbar";

  @NonNls String ADD_TO_WATCH = "Debugger.AddToWatch";

  @NonNls String XNEW_WATCH = "XDebugger.NewWatch";
  @NonNls String XREMOVE_WATCH = "XDebugger.RemoveWatch";
  @NonNls String XEDIT_WATCH = "XDebugger.EditWatch";
  @NonNls String XCOPY_WATCH = "XDebugger.CopyWatch";

  @NonNls String COPY_VALUE = "XDebugger.CopyValue";
  @NonNls String SET_VALUE = "XDebugger.SetValue";

  @NonNls String MUTE_BREAKPOINTS = "XDebugger.MuteBreakpoints";

  @NonNls String TOGGLE_SORT_VALUES = "XDebugger.ToggleSortValues";

  @NonNls String INLINE_DEBUGGER = "XDebugger.Inline";

  @NonNls String AUTO_TOOLTIP = "XDebugger.AutoTooltip";
  @NonNls String AUTO_TOOLTIP_ON_SELECTION = "XDebugger.AutoTooltipOnSelection";

  @NonNls String MARK_OBJECT = "Debugger.MarkObject";

  @NonNls String FOCUS_ON_BREAKPOINT = "Debugger.FocusOnBreakpoint";
}
