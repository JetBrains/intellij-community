// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.util.net.NetUtils;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * @deprecated legacy API, not intended to be used.
 */
@Deprecated
public interface DebuggableRunConfiguration extends RunConfiguration {
  default @NotNull InetSocketAddress computeDebugAddress(RunProfileState state) throws ExecutionException {
    try {
      return new InetSocketAddress(InetAddress.getLoopbackAddress(), NetUtils.findAvailableSocketPort());
    }
    catch (IOException e) {
      throw new ExecutionException(XDebuggerBundle.message("error.message.cannot.find.available.port"), e);
    }
  }

  default Promise<InetSocketAddress> computeDebugAddressAsync(RunProfileState state) throws ExecutionException {
    return Promises.resolvedPromise(computeDebugAddress(state));
  }

  @NotNull
  XDebugProcess createDebugProcess(@NotNull InetSocketAddress socketAddress,
                                   @NotNull XDebugSession session,
                                   @Nullable ExecutionResult executionResult,
                                   @NotNull ExecutionEnvironment environment) throws ExecutionException;

  default boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return true;
  }
}