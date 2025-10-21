// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame;

import com.intellij.util.ThreeState;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XInstanceEvaluator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a value in debugger tree.
 * Override {@link XValueContainer#computeChildren} if value has a properties which should be shown as child nodes
 */
public abstract class XValue extends XValueContainer {
  /**
   * Start computing presentation of the value in the debugger tree and call {@link XValueNode#setPresentation(javax.swing.Icon, String, String, boolean)}
   * when computation is finished.
   * Note that this method is called from the Event Dispatch thread so it should return quickly.
   * @param node node
   * @param place where the node will be shown.
   */
  public abstract void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place);

  /**
   * @return expression which evaluates to the current value
   */
  public @Nullable String getEvaluationExpression() {
    return null;
  }

  /**
   * Asynchronously calculates expression which evaluates to the current value
   */
  public @NotNull Promise<XExpression> calculateEvaluationExpression() {
    String expression = getEvaluationExpression();
    XExpression res =
      expression != null ? XDebuggerUtil.getInstance().createExpression(expression, null, null, EvaluationMode.EXPRESSION) : null;
    return Promises.resolvedPromise(res);
  }

  /**
   * @return evaluator to calculate value of the current object instance
   */
  public @Nullable XInstanceEvaluator getInstanceEvaluator() {
    return null;
  }

  /**
   * @return {@link XValueModifier} instance which can be used to modify the value
   */
  public @Nullable XValueModifier getModifier() {
    return null;
  }

  /**
   * Asynchronously computes {@link XValueModifier} instance which can be used to modify the value.
   */
  @ApiStatus.Internal
  public @NotNull CompletableFuture<@Nullable XValueModifier> getModifierAsync() {
    return CompletableFuture.completedFuture(getModifier());
  }


  /**
   * Provides {@link CompletableFuture} which finishes when all XValue's methods can be used
   * (like {@link XValue#getModifier} or {@link XValueMarkerProvider#getMarker})
   */
  @ApiStatus.Internal
  public @NotNull CompletableFuture<Void> isReady() {
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Start computing source position of the value and call {@link XNavigatable#setSourcePosition(com.intellij.xdebugger.XSourcePosition)}
   * when computation is finished.
   * Note that this method is called from the Event Dispatch thread so it should return quickly.
   * @param navigatable navigatable
   */
  public void computeSourcePosition(@NotNull XNavigatable navigatable) {
    navigatable.setSourcePosition(null);
  }

  /**
   * Provide inline debugger data, return ability to provide, use
   * {@link ThreeState#UNSURE} if unsupported (default platform implementation will be used),
   * {@link ThreeState#YES} if applicable
   * {@link ThreeState#NO} if not applicable
   */
  public @NotNull ThreeState computeInlineDebuggerData(@NotNull XInlineDebuggerDataCallback callback) {
    return ThreeState.UNSURE;
  }

  /**
   * Return {@code true} from this method and override {@link #computeSourcePosition(XNavigatable)} if navigation to the source
   * is supported for the value
   * @return {@code true} if navigation to the value's source is supported
   */
  public boolean canNavigateToSource() {
    // should be false, but cannot be due to compatibility reasons
    return true;
  }

  /**
   * Return {@code true} from this method and override {@link #computeTypeSourcePosition(XNavigatable)} if navigation to the value's type
   * is supported for the value
   * @return {@code true} if navigation to the value's type is supported
   */
  public boolean canNavigateToTypeSource() {
    return false;
  }

  /**
   * Async version of {@link #canNavigateToTypeSource()}
   */
  public Promise<Boolean> canNavigateToTypeSourceAsync() {
    return Promises.resolvedPromise(canNavigateToTypeSource());
  }


  /**
   * Start computing source position of the value's type and call {@link XNavigatable#setSourcePosition(com.intellij.xdebugger.XSourcePosition)}
   * when computation is finished.
   * Note that this method is called from the Event Dispatch thread so it should return quickly.
   */
  public void computeTypeSourcePosition(@NotNull XNavigatable navigatable) {
    navigatable.setSourcePosition(null);
  }

  /**
   * This enables showing referrers for the value
   *
   * @return provider that creates an XValue returning objects that refer to the current value
   * or null if showing referrers for the value is disabled
   */
  public @Nullable XReferrersProvider getReferrersProvider() {
    return null;
  }

  /**
   * Provides additional information about XValue, which frontend may use.
   */
  @ApiStatus.Internal
  public @Nullable CompletableFuture<XDescriptor> getXValueDescriptorAsync() {
    return null;
  }
}