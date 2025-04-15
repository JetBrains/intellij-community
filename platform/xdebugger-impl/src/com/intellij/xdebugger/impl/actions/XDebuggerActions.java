// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

@ApiStatus.Internal
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

  @NonNls String SHOW_EXECUTION_POINT = "ShowExecutionPoint";
  @NonNls String JUMP_TO_SOURCE = "XDebugger.JumpToSource";
  @NonNls String JUMP_TO_TYPE_SOURCE = "XDebugger.JumpToTypeSource";

  @NonNls String EVALUATE_EXPRESSION = "EvaluateExpression";
  @NonNls String QUICK_EVALUATE_EXPRESSION = "QuickEvaluateExpression";

  @NonNls String TOOL_WINDOW_TOP_TOOLBAR_GROUP = "XDebugger.ToolWindow.TopToolbar";
  @NonNls String TOOL_WINDOW_TOP_TOOLBAR_3_GROUP = "XDebugger.ToolWindow.TopToolbar3";
  @NonNls String TOOL_WINDOW_TOP_TOOLBAR_3_EXTRA_GROUP = "XDebugger.ToolWindow.TopToolbar3.Extra";
  @NonNls String TOOL_WINDOW_LEFT_TOOLBAR_GROUP = "XDebugger.ToolWindow.LeftToolbar";
  @NonNls String EVALUATE_DIALOG_TREE_POPUP_GROUP = "XDebugger.Evaluation.Dialog.Tree.Popup";
  @NonNls String INSPECT_TREE_POPUP_GROUP_FRONTEND = "XDebugger.Inspect.Tree.Popup.Frontend";
  @NonNls String INSPECT_TREE_POPUP_GROUP = "XDebugger.Inspect.Tree.Popup";
  @NonNls String VARIABLES_TREE_TOOLBAR_GROUP = "XDebugger.Variables.Tree.Toolbar";
  @NonNls String VARIABLES_TREE_POPUP_GROUP = "XDebugger.Variables.Tree.Popup";
  @NonNls String WATCHES_TREE_POPUP_GROUP = "XDebugger.Watches.Tree.Popup";
  @NonNls String WATCHES_TREE_POPUP_GROUP_FRONTEND = "XDebugger.Inspect.Tree.Popup.Watches.Frontend";
  @NonNls String WATCHES_INLINE_POPUP_GROUP = "XDebugger.Watches.Inline.Popup";
  @NonNls String WATCHES_TREE_TOOLBAR_GROUP = "XDebugger.Watches.Tree.Toolbar";
  @NonNls String FRAMES_TREE_POPUP_GROUP = "XDebugger.Frames.Tree.Popup";
  @NonNls String THREADS_TREE_POPUP_GROUP = "XDebugger.Threads.Tree.Popup";
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

  @NonNls String MARK_OBJECT = "Debugger.MarkObject";

  @NonNls String FOCUS_ON_BREAKPOINT = "Debugger.FocusOnBreakpoint";
  @NonNls String FOCUS_ON_FINISH = "Debugger.FocusOnFinish";

  @NonNls String PARALLEL_STACKS_POPUP_EXTRA_GROUP = "XDebugger.ParallelStacks.Popup.Extra";
  @NonNls String PARALLEL_STACKS_TOOLBAR_EXTRA_GROUP = "XDebugger.ParallelStacks.ToolBar.Extra";
}
