/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.debugger.connection

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.EventDispatcher
import com.intellij.util.io.socketConnection.ConnectionState
import com.intellij.util.io.socketConnection.ConnectionStatus
import com.intellij.util.io.socketConnection.SocketConnectionListener
import org.jetbrains.annotations.TestOnly
import org.jetbrains.debugger.DebugEventListener
import org.jetbrains.debugger.Vm
import org.jetbrains.util.concurrency.AsyncPromise
import org.jetbrains.util.concurrency.Promise
import org.jetbrains.util.concurrency.ResolvedPromise
import org.jetbrains.util.concurrency.pending
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.event.HyperlinkListener

public abstract class VmConnection<T : Vm> : Disposable, BrowserConnection {
  private val state = AtomicReference(ConnectionState(ConnectionStatus.NOT_CONNECTED))
  private val dispatcher = EventDispatcher.create(javaClass<DebugEventListener>())
  private val connectionDispatcher = EventDispatcher.create(javaClass<SocketConnectionListener>())

  public volatile var vm: T? = null
    protected set

  private val opened = AsyncPromise<Any?>()
  private val closed = AtomicBoolean()

  override fun getState() = state.get()

  public fun addDebugListener(listener: DebugEventListener) {
    dispatcher.addListener(listener)
  }

  @TestOnly
  public fun opened(): Promise<*> = opened

  override fun executeOnStart(runnable: Runnable) {
    opened.done { runnable.run() }
  }

  protected fun setState(status: ConnectionStatus, message: String? = null, messageLinkListener: HyperlinkListener? = null) {
    val newState = ConnectionState(status, message, messageLinkListener)
    val oldState = state.getAndSet(newState)
    if (oldState == null || oldState.getStatus() != status) {
      if (status == ConnectionStatus.CONNECTION_FAILED) {
        opened.setError(newState.getMessage())
      }
      connectionDispatcher.getMulticaster().statusChanged(status)
    }
  }

  override fun addListener(listener: SocketConnectionListener) {
    connectionDispatcher.addListener(listener)
  }

  protected val debugEventListener: DebugEventListener
    get() = dispatcher.getMulticaster()

  protected open fun startProcessing() {
    opened.setResult(null)
  }

  public fun close(message: String?, status: ConnectionStatus) {
    if (!closed.compareAndSet(false, true)) {
      return
    }

    if (opened.pending) {
      opened.setError("closed")
    }
    setState(status, message)
    Disposer.dispose(this, false)
  }

  override fun dispose() {
    vm = null
  }

  public open fun detachAndClose(): Promise<*> {
    if (opened.pending) {
      opened.setError("detached and closed")
    }

    val currentVm = vm
    val callback: Promise<*>
    if (currentVm == null) {
      callback = ResolvedPromise()
    }
    else {
      vm = null
      callback = currentVm.attachStateManager.detach()
    }
    close(null, ConnectionStatus.DISCONNECTED)
    return callback
  }
}