// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame;

import com.intellij.xdebugger.Obsolescent;
import com.intellij.xdebugger.XDebuggerBundle;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

/**
 * Supports asynchronous fetching full text of a value. If full text is already computed use {@link ImmediateFullValueEvaluator}
 *
 * @see XValueNode#setFullValueEvaluator
 * @see ImmediateFullValueEvaluator
 */
public abstract class XFullValueEvaluator {
  private final @Nls String myLinkText;

  private final @Nullable LinkAttributes myLinkAttributes;

  private boolean myShowValuePopup = true;

  private final MutableStateFlow<Boolean> myIsEnabledFlow = StateFlowKt.MutableStateFlow(true);

  protected XFullValueEvaluator() {
    this(XDebuggerBundle.message("node.test.show.full.value"));
  }

  protected XFullValueEvaluator(int actualLength) {
    this(XDebuggerBundle.message("node.text.ellipsis.truncated", actualLength));
  }

  /**
   * @param linkText text of the link what will be appended to a variables tree node text
   */
  protected XFullValueEvaluator(@NotNull @Nls String linkText) {
    this(linkText, null);
  }

  protected XFullValueEvaluator(@NotNull @Nls String linkText, @Nullable LinkAttributes linkAttributes) {
    myLinkText = linkText;
    myLinkAttributes = linkAttributes;
  }

  public boolean isShowValuePopup() { return myShowValuePopup; }

  public boolean isEnabled() { return myIsEnabledFlow.getValue(); }

  public @NotNull XFullValueEvaluator setShowValuePopup(boolean value) {
    myShowValuePopup = value;
    return this;
  }

  public @NotNull XFullValueEvaluator setIsEnabled(boolean value) {
    myIsEnabledFlow.setValue(value);
    return this;
  }

  @ApiStatus.Internal
  public StateFlow<Boolean> getIsEnabledFlow() {
    return myIsEnabledFlow;
  }

  /**
   * Start fetching full text of the value. Note that this method is called from the Event Dispatch Thread so it should return quickly
   *
   * @param callback used to notify that the full text has been successfully evaluated or an error occurs
   */
  public abstract void startEvaluation(@NotNull XFullValueEvaluationCallback callback);

  public @Nls @NotNull String getLinkText() {
    return myLinkText;
  }

  public @Nullable LinkAttributes getLinkAttributes() { return myLinkAttributes; }

  public interface XFullValueEvaluationCallback extends Obsolescent, XValueCallback {
    default void evaluated(@NotNull String fullValue) {
      evaluated(fullValue, null);
    }

    void evaluated(@NotNull String fullValue, @Nullable Font font);
  }

  public static class LinkAttributes {
    private final @Nullable @Nls String myLinkTooltipText;

    private final @Nullable Supplier<String> myShortcutSupplier;

    private final @Nullable Icon myLinkIcon;

    public LinkAttributes(@Nullable @Nls String text, @Nullable Supplier<String> supplier, @Nullable Icon icon) {
      myLinkTooltipText = text;
      myShortcutSupplier = supplier;
      myLinkIcon = icon;
    }

    public @Nullable Icon getLinkIcon() {
      return myLinkIcon;
    }

    public @Nls @Nullable String getLinkTooltipText() {
      return myLinkTooltipText;
    }

    public @Nullable Supplier<String> getShortcutSupplier() {
      return myShortcutSupplier;
    }
  }
}