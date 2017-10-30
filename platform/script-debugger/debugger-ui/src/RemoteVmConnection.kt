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
package org.jetbrains.debugger.connection

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Condition
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.util.io.connectRetrying
import com.intellij.util.io.socketConnection.ConnectionStatus
import io.netty.bootstrap.Bootstrap
import org.jetbrains.concurrency.*
import org.jetbrains.debugger.Vm
import org.jetbrains.io.NettyUtil
import org.jetbrains.rpc.LOG
import java.net.ConnectException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import javax.swing.JList

abstract class RemoteVmConnection : VmConnection<Vm>() {
  var address: InetSocketAddress? = null

  private val connectCancelHandler = AtomicReference<() -> Unit>()
  
  abstract fun createBootstrap(address: InetSocketAddress, vmResult: AsyncPromise<Vm>): Bootstrap
  
  @JvmOverloads
  fun open(address: InetSocketAddress, stopCondition: Condition<Void>? = null): Promise<Vm> {
    if (address.isUnresolved) {
      val error = "Host ${address.hostString} is unresolved"
      setState(ConnectionStatus.CONNECTION_FAILED, error)
      return rejectedPromise(error)
    }

    this.address = address
    setState(ConnectionStatus.WAITING_FOR_CONNECTION, "Connecting to ${address.hostString}:${address.port}")

    val result = AsyncPromise<Vm>()
    result
      .done {
        connectionSucceeded(it, address)
      }
      .rejected {
        if (it !is ConnectException) {
          LOG.errorIfNotMessage(it)
        }
        setState(ConnectionStatus.CONNECTION_FAILED, it.message)
      }
      .processed {
        connectCancelHandler.set(null)
      }
    
    val future = ApplicationManager.getApplication().executeOnPooledThread {
      if (Thread.interrupted()) {
        return@executeOnPooledThread
      }
      connectCancelHandler.set { result.setError("Closed explicitly") }

      doOpen(result, address, stopCondition)
    }

    connectCancelHandler.set {
      try {
        future.cancel(true)
      }
      finally {
        result.setError("Cancelled")
      }
    }
    return result
  }

  protected fun connectionSucceeded(it: Vm, address: InetSocketAddress) {
    vm = it
    setState(ConnectionStatus.CONNECTED, "Connected to ${connectedAddressToPresentation(address, it)}")
    startProcessing()
  }

  protected open fun doOpen(result: AsyncPromise<Vm>, address: InetSocketAddress, stopCondition: Condition<Void>?) {
    val maxAttemptCount = if (stopCondition == null) NettyUtil.DEFAULT_CONNECT_ATTEMPT_COUNT else -1
    val connectResult = createBootstrap(address, result).connectRetrying(address, maxAttemptCount, stopCondition)
    connectResult.handleError(Consumer { result.setError(it) })
    connectResult.handleThrowable(Consumer { result.setError(it) })
    val channel = connectResult.channel
    channel?.closeFuture()?.addListener {
      if (result.isFulfilled) {
        close("Process disconnected unexpectedly", ConnectionStatus.DISCONNECTED)
      }
    }
    if (channel != null) {
      stateChanged {
        if (it.status == ConnectionStatus.DISCONNECTED) {
          channel.close()
        }
      }
    }
  }

  protected open fun connectedAddressToPresentation(address: InetSocketAddress, vm: Vm): String = "${address.hostName}:${address.port}"

  override fun detachAndClose(): Promise<*> {
    try {
      connectCancelHandler.getAndSet(null)?.invoke()
    }
    finally {
      return super.detachAndClose()
    }
  }
}

fun RemoteVmConnection.open(address: InetSocketAddress, processHandler: ProcessHandler) = open(address, Condition<java.lang.Void> { processHandler.isProcessTerminating || processHandler.isProcessTerminated })

fun <T> chooseDebuggee(targets: Collection<T>, selectedIndex: Int, renderer: (T, ColoredListCellRenderer<*>) -> Unit): Promise<T> {
  if (targets.size == 1) {
    return resolvedPromise(targets.first())
  }
  else if (targets.isEmpty()) {
    return rejectedPromise("No tabs to inspect")
  }

  val result = org.jetbrains.concurrency.AsyncPromise<T>()
  ApplicationManager.getApplication().invokeLater {
    val list = JBList(targets)
    list.cellRenderer = object : ColoredListCellRenderer<T>() {
      override fun customizeCellRenderer(list: JList<out T>, value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
        renderer(value, this)
      }
    }
    if (selectedIndex != -1) {
      list.selectedIndex = selectedIndex
    }

    JBPopupFactory.getInstance()
      .createListPopupBuilder(list)
      .setTitle("Choose Page to Debug")
      .setCancelOnWindowDeactivation(false)
      .setItemChoosenCallback {
        @Suppress("UNCHECKED_CAST")
        val value = list.selectedValue
        if (value == null) {
          result.setError("No target to inspect")
        }
        else {
          result.setResult(value)
        }
      }
      .createPopup()
      .showInFocusCenter()
  }
  return result
}