// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import org.jetbrains.annotations.NotNull;

/**
 * @author gregsh
 */
public interface SMCustomMessagesParsing {

  OutputToGeneralTestEventsConverter createTestEventsConverter(final @NotNull String testFrameworkName,
                                                               final @NotNull TestConsoleProperties consoleProperties);

}
