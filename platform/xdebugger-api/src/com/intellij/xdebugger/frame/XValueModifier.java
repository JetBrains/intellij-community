// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame;

import com.intellij.xdebugger.XExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XValueModifier {

  /**
   * @deprecated Use {@link #setValue(XExpression, XModificationCallback)} instead
   */
  @Deprecated(forRemoval = true)
  public void setValue(@NotNull String expression, @NotNull XModificationCallback callback) {
    throw new AbstractMethodError();
  }

  /**
   * Start modification of the value. Note that this method is called from the Event Dispatch Thread so it should return quickly
   * @param expression new value
   * @param callback used to notify that value has been successfully modified or an error occurs
   */
  public void setValue(@NotNull XExpression expression, @NotNull XModificationCallback callback) {
    setValue(expression.getExpression(), callback);
  }

  /**
   * @return return text to show in expression editor when "Set Value" action is invoked
   */
  public @Nullable String getInitialValueEditorText() {
    return null;
  }

  /**
   * Asynchronously calculates initial value
   */
  public void calculateInitialValueEditorText(XInitialValueCallback callback) {
    callback.setValue(getInitialValueEditorText());
  }

  public interface XModificationCallback extends XValueCallback {
    void valueModified();
  }

  public interface XInitialValueCallback {
    void setValue(String initialValue);
  }
}