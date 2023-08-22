/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

public interface DebuggableRunConfiguration extends RunConfiguration {
  @NotNull
  default InetSocketAddress computeDebugAddress(RunProfileState state) throws ExecutionException {
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