package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XValueModifier {

  /**
   * Start modification of the value.
   * @param expression new value
   * @param callback used to notify that value has been successfully modified or an error occurs
   */
  public abstract void setValue(@NotNull String expression, @NotNull XModificationCallback callback);

  /**
   * @return return text to show in expression editor when "Set Value" action is invoked
   */
  @Nullable
  public String getInitialValueEditorText() {
    return null;
  }

  public interface XModificationCallback {
    void valueModified();

    void errorOccurred(@NotNull String errorMessage);

    /**
     * @deprecated use {@link XModificationCallback#errorOccurred(String)}
     */
    @Deprecated
    void errorOccured(@NotNull String errorMessage);
  }
}
