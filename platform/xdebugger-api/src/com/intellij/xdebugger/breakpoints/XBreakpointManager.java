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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * Use {@link com.intellij.xdebugger.XDebuggerManager#getBreakpointManager()} to obtain instance of this service
 *
 * @author nik
 */
public interface XBreakpointManager {
  @NotNull
  <T extends XBreakpointProperties> XBreakpoint<T> addBreakpoint(XBreakpointType<XBreakpoint<T>, T> type, @Nullable T properties);

  @NotNull
  <T extends XBreakpointProperties> XLineBreakpoint<T> addLineBreakpoint(XLineBreakpointType<T> type,
                                                                         @NotNull String fileUrl,
                                                                         int line,
                                                                         @Nullable T properties,
                                                                         boolean temporary);

  @NotNull
  <T extends XBreakpointProperties> XLineBreakpoint<T> addLineBreakpoint(XLineBreakpointType<T> type,
                                                                         @NotNull String fileUrl,
                                                                         int line,
                                                                         @Nullable T properties);

  void removeBreakpoint(@NotNull XBreakpoint<?> breakpoint);

  @NotNull
  XBreakpoint<?>[] getAllBreakpoints();

  @NotNull
  <B extends XBreakpoint<?>> Collection<? extends B> getBreakpoints(@NotNull XBreakpointType<B, ?> type);

  @NotNull
  <B extends XBreakpoint<?>> Collection<? extends B> getBreakpoints(@NotNull Class<? extends XBreakpointType<B, ?>> typeClass);

  @Nullable
  <P extends XBreakpointProperties> XLineBreakpoint<P> findBreakpointAtLine(@NotNull XLineBreakpointType<P> type,
                                                                            @NotNull VirtualFile file,
                                                                            int line);

  boolean isDefaultBreakpoint(@NotNull XBreakpoint<?> breakpoint);

  @Nullable
  <B extends XBreakpoint<?>> B getDefaultBreakpoint(@NotNull XBreakpointType<B, ?> type);

  <B extends XBreakpoint<P>, P extends XBreakpointProperties> void addBreakpointListener(@NotNull XBreakpointType<B, P> type,
                                                                                         @NotNull XBreakpointListener<B> listener);

  <B extends XBreakpoint<P>, P extends XBreakpointProperties> void removeBreakpointListener(@NotNull XBreakpointType<B, P> type,
                                                                                            @NotNull XBreakpointListener<B> listener);

  <B extends XBreakpoint<P>, P extends XBreakpointProperties> void addBreakpointListener(@NotNull XBreakpointType<B, P> type,
                                                                                         @NotNull XBreakpointListener<B> listener,
                                                                                         Disposable parentDisposable);

  void addBreakpointListener(@NotNull XBreakpointListener<XBreakpoint<?>> listener);

  void removeBreakpointListener(@NotNull XBreakpointListener<XBreakpoint<?>> listener);

  void addBreakpointListener(@NotNull XBreakpointListener<XBreakpoint<?>> listener, @NotNull Disposable parentDisposable);

  void updateBreakpointPresentation(@NotNull XLineBreakpoint<?> breakpoint, @Nullable Icon icon, @Nullable String errorMessage);
}
