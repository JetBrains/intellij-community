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

package com.intellij.xdebugger.breakpoints;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XBreakpointHandler<B extends XBreakpoint<?>> {
  public static final XBreakpointHandler<?>[] EMPTY_ARRAY = new XBreakpointHandler<?>[0];
  private final Class<? extends XBreakpointType<B, ?>> myBreakpointTypeClass;

  protected XBreakpointHandler(@NotNull Class<? extends XBreakpointType<B, ?>> breakpointTypeClass) {
    myBreakpointTypeClass = breakpointTypeClass;
  }

  public final Class<? extends XBreakpointType<B, ?>> getBreakpointTypeClass() {
    return myBreakpointTypeClass;
  }

  /**
   * Called when a breakpoint need to be registered in the debugging engine
   * @param breakpoint breakpoint to register
   */
  public abstract void registerBreakpoint(@NotNull B breakpoint);

  /**
   * Called when a breakpoint need to be unregistered from the debugging engine
   * @param breakpoint breakpoint to unregister
   * @param temporary determines whether {@code breakpoint} is unregistered forever or it may be registered again. This parameter may
   * be used for performance purposes. For example the breakpoint may be disabled rather than removed in the debugging engine if
   * {@code temporary} is {@code true}
   */
  public abstract void unregisterBreakpoint(@NotNull B breakpoint, final boolean temporary);

}
