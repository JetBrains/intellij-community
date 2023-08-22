// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.stepping;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.List;

/**
 * Implement this class and return its instance from {@link com.intellij.xdebugger.XDebugProcess#getSmartStepIntoHandler()} to support
 * Smart Step Into action
 */
public abstract class XSmartStepIntoHandler<Variant extends XSmartStepIntoVariant> {

  /**
   * @param position current position
   * @return list of function/method calls containing in the current line
   */
  @NotNull
  public abstract List<Variant> computeSmartStepVariants(@NotNull XSourcePosition position);

  /**
   * @param position current position
   * @return list of function/method calls containing in the current line
   */
  @NotNull
  public Promise<List<Variant>> computeSmartStepVariantsAsync(@NotNull XSourcePosition position) {
    return Promises.resolvedPromise(computeSmartStepVariants(position));
  }

  /**
   * List of variants for the regular step into, if supported
   * @param position current position
   * @return list of function/method calls containing in the current line
   */
  @NotNull
  public Promise<List<Variant>> computeStepIntoVariants(@NotNull XSourcePosition position) {
    return Promises.rejectedPromise();
  }

  /**
   * Resume execution and call {@link XDebugSession#positionReached(XSuspendContext)}
   * when {@code variant} function/method is reached
   * @param variant selected variant
   */
  public void startStepInto(@NotNull Variant variant) {
    throw new AbstractMethodError();
  }

  public void startStepInto(@NotNull Variant variant, @Nullable XSuspendContext context) {
    startStepInto(variant);
  }

  /**
   * Action if no variants detected, defaults to step into
   */
  public void stepIntoEmpty(XDebugSession session) {
    session.stepInto();
  }

  /**
   * @return title for popup which will be shown to select method/function
   * @param position current position
   */
  public abstract @NlsContexts.PopupTitle String getPopupTitle(@NotNull XSourcePosition position);
}
