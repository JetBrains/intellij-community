// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.frame;

import com.intellij.xdebugger.Obsolescent;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Supports asynchronous fetching full text of a value. If full text is already computed use {@link ImmediateFullValueEvaluator}
 *
 * @see XValueNode#setFullValueEvaluator
 * @see ImmediateFullValueEvaluator
 */
public abstract class XFullValueEvaluator {
  private final @Nls String myLinkText;
  private boolean myShowValuePopup = true;

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
    myLinkText = linkText;
  }

  public boolean isShowValuePopup() {
    return myShowValuePopup;
  }

  @NotNull
  public XFullValueEvaluator setShowValuePopup(boolean value) {
    myShowValuePopup = value;
    return this;
  }

  /**
   * Start fetching full text of the value. Note that this method is called from the Event Dispatch Thread so it should return quickly
   *
   * @param callback used to notify that the full text has been successfully evaluated or an error occurs
   */
  public abstract void startEvaluation(@NotNull XFullValueEvaluationCallback callback);

  public @Nls String getLinkText() {
    return myLinkText;
  }

  public interface XFullValueEvaluationCallback extends Obsolescent, XValueCallback {
    void evaluated(@NotNull String fullValue);

    void evaluated(@NotNull String fullValue, @Nullable Font font);
  }
}