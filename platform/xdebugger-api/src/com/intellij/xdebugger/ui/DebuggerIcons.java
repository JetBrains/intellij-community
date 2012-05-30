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

package com.intellij.xdebugger.ui;

import com.intellij.icons.AllIcons;

import javax.swing.*;

/**
 * @author nik
 */
public interface DebuggerIcons {

  Icon ENABLED_BREAKPOINT_ICON = AllIcons.Debugger.Db_set_breakpoint;
  Icon MUTED_BREAKPOINT_ICON = AllIcons.Debugger.Db_muted_breakpoint;
  Icon DISABLED_BREAKPOINT_ICON = AllIcons.Debugger.Db_disabled_breakpoint;
  Icon MUTED_DISABLED_BREAKPOINT_ICON = AllIcons.Debugger.Db_muted_disabled_breakpoint;
  Icon INVALID_BREAKPOINT_ICON = AllIcons.Debugger.Db_invalid_breakpoint;
  Icon MUTED_INVALID_BREAKPOINT_ICON = AllIcons.Debugger.Db_muted_invalid_breakpoint;
  Icon VERIFIED_BREAKPOINT_ICON = AllIcons.Debugger.Db_verified_breakpoint;
  Icon MUTED_VERIFIED_BREAKPOINT_ICON = AllIcons.Debugger.Db_muted_verified_breakpoint;
  Icon DISABLED_DEPENDENT_BREAKPOINT_ICON = AllIcons.Debugger.Db_dep_line_breakpoint;
  Icon MUTED_DISABLED_DEPENDENT_BREAKPOINT_ICON = AllIcons.Debugger.Db_muted_dep_line_breakpoint;
  Icon VERIFIED_WARNING_BREAKPOINT_ICON = AllIcons.Debugger.Db_verified_warning_breakpoint;

  Icon ENABLED_EXCEPTION_BREAKPOINT_ICON = AllIcons.Debugger.Db_exception_breakpoint;
  Icon DISABLED_EXCEPTION_BREAKPOINT_ICON = AllIcons.Debugger.Db_disabled_exception_breakpoint;
  Icon DISABLED_DEPENDENT_EXCEPTION_BREAKPOINT_ICON = AllIcons.Debugger.Db_dep_exception_breakpoint;

  Icon VALUE_ICON = AllIcons.Debugger.Value;
  Icon ARRAY_VALUE_ICON = AllIcons.Debugger.Db_array;
  Icon PRIMITIVE_VALUE_ICON = AllIcons.Debugger.Db_primitive;
  Icon WATCHED_VALUE_ICON = AllIcons.Debugger.Watch;

  Icon STACK_FRAME_ICON = AllIcons.Debugger.StackFrame;
  Icon CURRENT_THREAD_ICON = AllIcons.Debugger.ThreadCurrent;
  Icon SUSPENDED_THREAD_ICON = AllIcons.Debugger.ThreadSuspended;
}
