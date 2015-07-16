package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import org.jetbrains.annotations.NotNull;

public interface SMRootTestProxyFormatter {
  void format(@NotNull SMTestProxy.SMRootTestProxy testProxy, @NotNull TestTreeRenderer renderer);
}
