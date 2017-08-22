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
package org.jetbrains.debugger.connection

import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.socketConnection.ConnectionState
import com.intellij.util.io.socketConnection.ConnectionStatus
import com.intellij.util.io.socketConnection.SocketConnectionListener
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.*
import org.jetbrains.debugger.DebugEventListener
import org.jetbrains.debugger.Vm
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.event.HyperlinkListener

abstract class VmConnection<T : Vm> : Disposable {
  open val browser: WebBrowser? = null

  private val stateRef = AtomicReference(ConnectionState(ConnectionStatus.NOT_CONNECTED))

  open protected val dispatcher: EventDispatcher<DebugEventListener> = EventDispatcher.create(DebugEventListener::class.java)
  private val connectionDispatcher = ContainerUtil.createLockFreeCopyOnWriteList<(ConnectionState) -> Unit>()

  @Volatile var vm: T? = null
    protected set

  private val opened = AsyncPromise<Any?>()
  private val closed = AtomicBoolean()

  val state: ConnectionState
    get() = stateRef.get()

  fun addDebugListener(listener: DebugEventListener) {
    dispatcher.addListener(listener)
  }

  @TestOnly
  fun opened(): Promise<*> = opened

  fun executeOnStart(runnable: Runnable) {
    opened.done { runnable.run() }
  }

  protected fun setState(status: ConnectionStatus, message: String? = null, messageLinkListener: HyperlinkListener? = null) {
    val newState = ConnectionState(status, message, messageLinkListener)
    val oldState = stateRef.getAndSet(newState)
    if (oldState == null || oldState.status != status) {
      if (status == ConnectionStatus.CONNECTION_FAILED) {
        opened.setError(newState.message)
      }
      for (listener in connectionDispatcher) {
        listener(newState)
      }
    }
  }

  fun stateChanged(listener: (ConnectionState) -> Unit) {
    connectionDispatcher.add(listener)
  }

  // backward compatibility, go debugger
  fun addListener(listener: SocketConnectionListener) {
    stateChanged { listener.statusChanged(it.status) }
  }

  protected val debugEventListener: DebugEventListener
    get() = dispatcher.multicaster

  protected open fun startProcessing() {
    opened.setResult(null)
  }

  fun close(message: String?, status: ConnectionStatus) {
    if (!closed.compareAndSet(false, true)) {
      return
    }

    if (opened.isPending) {
      opened.setError("closed")
    }
    setState(status, message)
    Disposer.dispose(this, false)
  }

  override fun dispose() {
    vm = null
  }

  open fun detachAndClose(): Promise<*> {
    if (opened.isPending) {
      opened.setError(createError("detached and closed"))
    }

    val currentVm = vm
    val callback: Promise<*>
    if (currentVm == null) {
      callback = nullPromise()
    }
    else {
      vm = null
      callback = currentVm.attachStateManager.detach()
    }
    close(null, ConnectionStatus.DISCONNECTED)
    return callback
  }
}