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

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Condition
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.util.io.socketConnection.ConnectionStatus
import io.netty.bootstrap.Bootstrap
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.rejectedPromise
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.debugger.Vm
import org.jetbrains.io.NettyUtil
import org.jetbrains.io.connect
import org.jetbrains.rpc.LOG
import java.net.ConnectException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

abstract class RemoteVmConnection : VmConnection<Vm>() {
  var port = -1

  private val connectCancelHandler = AtomicReference<() -> Unit>()

  abstract fun createBootstrap(address: InetSocketAddress, vmResult: org.jetbrains.concurrency.AsyncPromise<Vm>): Bootstrap

  @JvmOverloads
  fun open(address: InetSocketAddress, stopCondition: Condition<Void>? = null): Promise<Vm> {
    port = address.port
    setState(ConnectionStatus.WAITING_FOR_CONNECTION, "Connecting to ${address.hostName}:${port}")
    val result = AsyncPromise<Vm>()
    val future = ApplicationManager.getApplication().executeOnPooledThread {
      if (Thread.interrupted()) {
        return@executeOnPooledThread
      }

      connectCancelHandler.set { result.setError("Closed explicitly") }

      val connectionPromise = AsyncPromise<Any?>()
      connectionPromise.rejected { result.setError(it) }

      result
        .done {
          vm = it!!
          setState(ConnectionStatus.CONNECTED, "Connected to ${connectedAddressToPresentation(address, it)}")
          startProcessing()
        }
        .rejected {
          if (it !is ConnectException) {
            Promise.logError(LOG, it)
          }
          setState(ConnectionStatus.CONNECTION_FAILED, it.message)
        }
        .processed { connectCancelHandler.set(null) }

      createBootstrap(address, result).connect(address, connectionPromise, maxAttemptCount = if (stopCondition == null) NettyUtil.DEFAULT_CONNECT_ATTEMPT_COUNT else -1, stopCondition = stopCondition)
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

fun <T> chooseDebuggee(targets: Collection<T>, selectedIndex: Int, renderer: (T, ColoredListCellRenderer.KotlinFriendlyColoredListCellRenderer<*>) -> Unit): Promise<T> {
  if (targets.size == 1) {
    return resolvedPromise(targets.first())
  }
  else if (targets.isEmpty()) {
    return rejectedPromise("No tabs to inspect")
  }

  val result = org.jetbrains.concurrency.AsyncPromise<T>()
  ApplicationManager.getApplication().invokeLater {
    val list = JBList(targets)
    list.cellRenderer = object : ColoredListCellRenderer.KotlinFriendlyColoredListCellRenderer<T>() {
      override fun customizeCellRenderer(value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
        renderer(value, this)
      }
    }
    if (selectedIndex != -1) {
      list.selectedIndex = selectedIndex
    }

    JBPopupFactory.getInstance()
      .createListPopupBuilder(list)
      .setTitle("Choose Page to Debug")
      .setItemChoosenCallback {
        @Suppress("UNCHECKED_CAST")
        val value = list.selectedValue as T
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