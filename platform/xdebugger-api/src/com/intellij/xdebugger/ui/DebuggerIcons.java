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

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author nik
 */
public interface DebuggerIcons {

  Icon ENABLED_BREAKPOINT_ICON = IconLoader.getIcon("/debugger/db_set_breakpoint.png");
  Icon MUTED_BREAKPOINT_ICON = IconLoader.getIcon("/debugger/db_muted_breakpoint.png");
  Icon DISABLED_BREAKPOINT_ICON = IconLoader.getIcon("/debugger/db_disabled_breakpoint.png");
  Icon MUTED_DISABLED_BREAKPOINT_ICON = IconLoader.getIcon("/debugger/db_muted_disabled_breakpoint.png");
  Icon INVALID_BREAKPOINT_ICON = IconLoader.getIcon("/debugger/db_invalid_breakpoint.png");
  Icon MUTED_INVALID_BREAKPOINT_ICON = IconLoader.getIcon("/debugger/db_muted_invalid_breakpoint.png");
  Icon VERIFIED_BREAKPOINT_ICON = IconLoader.getIcon("/debugger/db_verified_breakpoint.png");
  Icon MUTED_VERIFIED_BREAKPOINT_ICON = IconLoader.getIcon("/debugger/db_muted_verified_breakpoint.png");
  Icon DISABLED_DEPENDENT_BREAKPOINT_ICON = IconLoader.getIcon("/debugger/db_dep_line_breakpoint.png");
  Icon MUTED_DISABLED_DEPENDENT_BREAKPOINT_ICON = IconLoader.getIcon("/debugger/db_muted_dep_line_breakpoint.png");

  Icon VALUE_ICON = IconLoader.getIcon("/debugger/value.png");
  Icon ARRAY_VALUE_ICON = IconLoader.getIcon("/debugger/db_array.png");
  Icon PRIMITIVE_VALUE_ICON = IconLoader.getIcon("/debugger/db_primitive.png");
  Icon WATCHED_VALUE_ICON = IconLoader.getIcon("/debugger/watch.png");

  Icon STACK_FRAME_ICON = IconLoader.getIcon("/debugger/stackFrame.png");
  Icon SUSPENDED_THREAD_ICON = IconLoader.getIcon("/debugger/threadSuspended.png");
}
