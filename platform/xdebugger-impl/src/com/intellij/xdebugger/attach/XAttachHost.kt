/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xdebugger.attach

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessInfo
import com.intellij.openapi.progress.runBlockingCancellable

/**
 * This interface describes the host(local or remote), from which list of processes can be obtained
 */
interface XAttachHost {
  /**
   * @return a list of running processes on this host
   */
  @Throws(ExecutionException::class)
  @Deprecated("Use getProcessListAsync", replaceWith = ReplaceWith("getProcessListAsync"))
  fun getProcessList(): List<ProcessInfo>

  /**
   * @return a list of running processes on this host
   */
  @Suppress("DEPRECATION")
  @Throws(ExecutionException::class)
  suspend fun getProcessListAsync(): List<ProcessInfo> = getProcessList()

  @Suppress("OVERRIDE_DEPRECATION")
  interface Async : XAttachHost {
    override fun getProcessList(): List<ProcessInfo> = runBlockingCancellable { getProcessListAsync() }
    override suspend fun getProcessListAsync(): List<ProcessInfo>
  }
}