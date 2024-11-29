// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xdebugger.breakpoints;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Set;

/**
 * Use {@link com.intellij.xdebugger.XDebuggerManager#getBreakpointManager()} to obtain instance of this service
 */
@ApiStatus.NonExtendable
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

  XBreakpoint<?> @NotNull [] getAllBreakpoints();

  @NotNull
  <B extends XBreakpoint<?>> Collection<? extends B> getBreakpoints(@NotNull XBreakpointType<B, ?> type);

  @NotNull
  <B extends XBreakpoint<?>> Collection<? extends B> getBreakpoints(@NotNull Class<? extends XBreakpointType<B, ?>> typeClass);

  @NotNull
  <B extends XLineBreakpoint<P>, P extends XBreakpointProperties> Collection<B> findBreakpointsAtLine(@NotNull XLineBreakpointType<P> type,
                                                                                                      @NotNull VirtualFile file,
                                                                                                      int line);

  /**
   * @deprecated Use {@link #findBreakpointsAtLine}.
   */
  @Deprecated(forRemoval = true)
  @Nullable
  <P extends XBreakpointProperties> XLineBreakpoint<P> findBreakpointAtLine(@NotNull XLineBreakpointType<P> type,
                                                                            @NotNull VirtualFile file,
                                                                            int line);

  boolean isDefaultBreakpoint(@NotNull XBreakpoint<?> breakpoint);

  @NotNull
  <B extends XBreakpoint<?>> Set<B> getDefaultBreakpoints(@NotNull XBreakpointType<B, ?> type);

  <B extends XBreakpoint<P>, P extends XBreakpointProperties> void addBreakpointListener(@NotNull XBreakpointType<B, P> type,
                                                                                         @NotNull XBreakpointListener<B> listener);

  <B extends XBreakpoint<P>, P extends XBreakpointProperties> void removeBreakpointListener(@NotNull XBreakpointType<B, P> type,
                                                                                            @NotNull XBreakpointListener<B> listener);

  <B extends XBreakpoint<P>, P extends XBreakpointProperties> void addBreakpointListener(@NotNull XBreakpointType<B, P> type,
                                                                                         @NotNull XBreakpointListener<B> listener,
                                                                                         Disposable parentDisposable);

  void updateBreakpointPresentation(@NotNull XLineBreakpoint<?> breakpoint, @Nullable Icon icon, @Nullable String errorMessage);
}
