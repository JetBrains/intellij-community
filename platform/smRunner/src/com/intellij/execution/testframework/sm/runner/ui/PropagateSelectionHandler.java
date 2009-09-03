package com.intellij.execution.testframework.sm.runner.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;

/**
 * @author Roman Chernyatchik
 *
 * Should be used when one component wan't to propagate (transmit) selection to
 * other component(with/without capturing focus)
 */
public interface PropagateSelectionHandler {
  void handlePropagateSelectionRequest(@Nullable SMTestProxy selectedTestProxy,
                                       @NotNull Object sender,
                                       boolean requestFocus);
}
