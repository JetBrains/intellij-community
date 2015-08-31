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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Condition
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.socketConnection.ConnectionStatus
import io.netty.bootstrap.Bootstrap
import org.jetbrains.debugger.Vm
import org.jetbrains.io.NettyUtil
import org.jetbrains.rpc.CommandProcessor
import org.jetbrains.util.concurrency.AsyncPromise
import org.jetbrains.util.concurrency.Promise
import org.jetbrains.util.concurrency.RejectedPromise
import org.jetbrains.util.concurrency.ResolvedPromise
import java.net.ConnectException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

public abstract class RemoteVmConnection : VmConnection<Vm>() {
  private val connectCancelHandler = AtomicReference<Runnable>()

  override fun getBrowser() = null

  public abstract fun createBootstrap(address: InetSocketAddress, vmResult: AsyncPromise<Vm>): Bootstrap

  public fun open(address: InetSocketAddress, stopCondition: Condition<Void>? = null) {
    setState(ConnectionStatus.WAITING_FOR_CONNECTION, "Connecting to ${address.getHostName()}:${address.getPort()}")
    val future = ApplicationManager.getApplication().executeOnPooledThread(object : Runnable {
      override fun run() {
        if (Thread.interrupted()) {
          return
        }

        val result = AsyncPromise<Vm>()
        connectCancelHandler.set(Runnable { result.setError("Closed explicitly") })

        val connectionPromise = AsyncPromise<Any?>()
        connectionPromise.rejected { result.setError(it) }

        result
          .done {
            vm = it
            setState(ConnectionStatus.CONNECTED, "Connected to ${connectedAddressToPresentation(address, it)}")
            startProcessing()
          }
          .rejected {
            if (it !is ConnectException) {
              Promise.logError(CommandProcessor.LOG, it)
            }
            setState(ConnectionStatus.CONNECTION_FAILED, it.getMessage())
          }
          .processed { connectCancelHandler.set(null) }

        NettyUtil.connect(createBootstrap(address, result), address, connectionPromise, if (stopCondition == null) NettyUtil.DEFAULT_CONNECT_ATTEMPT_COUNT else -1, stopCondition)
      }
    })
    connectCancelHandler.set(Runnable {  future.cancel(true) })
  }

  protected open fun connectedAddressToPresentation(address: InetSocketAddress, vm: Vm): String = address.getHostName() + ":" + address.getPort()

  override fun detachAndClose(): Promise<*> {
    try {
      connectCancelHandler.getAndSet(null)?.run()
    }
    finally {
      return super.detachAndClose()
    }
  }
}

public fun <T> chooseDebuggee(targets: Collection<T>, selectedIndex: Int, itemToString: (T) -> String): Promise<T> {
  if (targets.size() == 1) {
    return ResolvedPromise(ContainerUtil.getFirstItem(targets))
  }
  else if (targets.isEmpty()) {
    return RejectedPromise("No tabs to inspect")
  }

  val result = AsyncPromise<T>()
  ApplicationManager.getApplication().invokeLater(Runnable {
    val list = JBList(targets)
    list.setCellRenderer(object : ColoredListCellRenderer.KotlinFriendlyColoredListCellRenderer<T>() {
      override fun customizeCellRenderer(value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
        append(itemToString(value))
      }
    })
    if (selectedIndex != -1) {
      list.setSelectedIndex(selectedIndex)
    }

    JBPopupFactory.getInstance()
      .createListPopupBuilder(list)
      .setTitle("Choose Page to Debug")
      .setItemChoosenCallback(object : Runnable {
        override fun run() {
          @suppress("UNCHECKED_CAST")
          val value = list.getSelectedValue() as T
          if (value == null) {
            result.setError("No target to inspect")
          }
          else {
            result.setResult(value)
          }
        }
      })
      .createPopup()
      .showInFocusCenter()
  })
  return result
}