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

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project

/**
 * This interface describes the provider to enumerate different hosts/environments capable of attaching to with the debugger.
 * The examples are local machine environment (see [LocalAttachHost]) or SSH connection to a remote server.
 */
interface XAttachHostProvider<T : XAttachHost> {
  companion object {
    @JvmField
    val EP: ExtensionPointName<XAttachHostProvider<*>> = create<XAttachHostProvider<*>>("com.intellij.xdebugger.attachHostProvider")
  }

  /**
   * @return the group to which all connections provided by this provider belong
   */
  fun getPresentationGroup(): XAttachPresentationGroup<out XAttachHost>

  /**
   * @return a list of connections of this type, which is characterized by the provider
   */
  @Deprecated("Use getAvailableHostsAsync", replaceWith = ReplaceWith("getAvailableHostsAsync"))
  fun getAvailableHosts(project: Project?): List<T>

  /**
   * @return a list of connections of this type, which is characterized by the provider
   */
  @Suppress("DEPRECATION")
  suspend fun getAvailableHostsAsync(project: Project?) = getAvailableHosts(project)

  interface Async<T : XAttachHost> : XAttachHostProvider<T> {
    @Deprecated("Use getAvailableHostsAsync", replaceWith = ReplaceWith("getAvailableHostsAsync"))
    override fun getAvailableHosts(project: Project?): List<T> = runBlockingCancellable { getAvailableHostsAsync(project) }

    override suspend fun getAvailableHostsAsync(project: Project?): List<T>
  }
}
