/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.xdebugger.frame;

import com.intellij.xdebugger.Obsolescent;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Supports asynchronous fetching full text of a value. If full text is already computed use {@link ImmediateFullValueEvaluator}
 * @see XValueNode#setFullValueEvaluator
 * @see ImmediateFullValueEvaluator
 *
 * @author nik
 */
public abstract class XFullValueEvaluator {
  private final String myLinkText;

  protected XFullValueEvaluator() {
    this(XDebuggerBundle.message("node.test.show.full.value"));
  }

  protected XFullValueEvaluator(int actualLength) {
    this(XDebuggerBundle.message("node.text.ellipsis.truncated", actualLength));
  }

  /**
   * @param linkText text of the link what will be appended to a variables tree node text
   */
  protected XFullValueEvaluator(@NotNull String linkText) {
    myLinkText = linkText;
  }

  /**
   * Start fetching full text of the value. Note that this method is called from the Event Dispatch Thread so it should return quickly
   * @param callback used to notify that the full text has been successfully evaluated or an error occurs
   */
  public abstract void startEvaluation(@NotNull XFullValueEvaluationCallback callback);

  public String getLinkText() {
    return myLinkText;
  }

  public interface XFullValueEvaluationCallback extends Obsolescent, XValueCallback {
    void evaluated(@NotNull String fullValue);

    void evaluated(@NotNull String fullValue, @Nullable Font font);
  }
}