// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

  // no externals usages, agreed to keep it anyway for now
  // cannot be default because project message bus must be used
  /**
   * @deprecated Use {@link XBreakpointListener#TOPIC}
   */
  @Deprecated
  void addBreakpointListener(@NotNull XBreakpointListener<XBreakpoint<?>> listener, @NotNull Disposable parentDisposable);

  /**
   * @deprecated Use {@link XBreakpointListener#TOPIC}
   */
  @Deprecated
  void addBreakpointListener(@NotNull XBreakpointListener<XBreakpoint<?>> listener);

  /**
   * @deprecated Use {@link XBreakpointListener#TOPIC}
   */
  @Deprecated
  void removeBreakpointListener(@NotNull XBreakpointListener<XBreakpoint<?>> listener);

  void updateBreakpointPresentation(@NotNull XLineBreakpoint<?> breakpoint, @Nullable Icon icon, @Nullable String errorMessage);
}
