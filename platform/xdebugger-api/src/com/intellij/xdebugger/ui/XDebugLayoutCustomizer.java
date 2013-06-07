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
   * Creates {@code Content} instance for a given {@code console}.
   *
   * @param console {@code ExecutionConsole} instance
   * @param ui {@code RunnerLayoutUi} instance
   * @return {@code Content} instance
   */
  @NotNull
  Content createConsoleContent(@NotNull ExecutionConsole console, @NotNull RunnerLayoutUi ui);

  /**
   * Registers additional tabs for 'Debug' tool window.
   * @param ui {@code RunnerLayoutUi} instance
   */
  void registerAdditionalContent(@NotNull RunnerLayoutUi ui);
}
