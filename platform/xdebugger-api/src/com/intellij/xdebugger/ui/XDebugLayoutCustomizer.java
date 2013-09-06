package com.intellij.xdebugger.ui;

import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to customize xdebug layout for 'Debug' tool window.
 *
 * @author Sergey Simonchik
 */
public interface XDebugLayoutCustomizer {

  /**
   * Registers tab for the given {@code console}, that is returned by {@link com.intellij.xdebugger.XDebugProcess#createConsole()}.
   *
   * @param console {@code ExecutionConsole} instance
   * @param ui {@code RunnerLayoutUi} instance
   * @return registered {@code Content} instance
   */
  @NotNull
  Content registerConsoleContent(@NotNull ExecutionConsole console, @NotNull RunnerLayoutUi ui);

  /**
   * Registers additional tabs for 'Debug' tool window.
   * @param ui {@code RunnerLayoutUi} instance
   */
  void registerAdditionalContent(@NotNull RunnerLayoutUi ui);
}
