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

import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Condition
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.util.Consumer
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.socketConnection.ConnectionStatus
import io.netty.bootstrap.Bootstrap
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.Vm
import org.jetbrains.io.NettyUtil
import org.jetbrains.rpc.CommandProcessor

import javax.swing.*
import java.net.ConnectException
import java.net.InetSocketAddress
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

public abstract class RemoteVmConnection : VmConnection<Vm>() {
  private val connectCancelHandler = AtomicReference<Runnable>()

  override fun getBrowser() = null

  public abstract fun createBootstrap(address: InetSocketAddress, vmResult: AsyncPromise<Vm>): Bootstrap

  jvmOverloads public fun open(address: InetSocketAddress, stopCondition: Condition<Void>? = null) {
    setState(ConnectionStatus.WAITING_FOR_CONNECTION, "Connecting to ${address.getHostName()}:${address.getPort()}")
    val future = ApplicationManager.getApplication().executeOnPooledThread(object : Runnable {
      override fun run() {
        if (Thread.interrupted()) {
          return
        }

        val result = AsyncPromise<Vm>()
        connectCancelHandler.set(object : Runnable {
          override fun run() {
            result.setError(Promise.createError("Closed explicitly"))
          }
        })

        val connectionPromise = AsyncPromise<Void>()
        connectionPromise.rejected(object : Consumer<Throwable> {
          override fun consume(error: Throwable) {
            result.setError(error)
          }
        })

        result.done(object : Consumer<Vm> {
          override fun consume(vm: Vm) {
            this@RemoteVmConnection.vm = vm
            setState(ConnectionStatus.CONNECTED, "Connected to " + connectedAddressToPresentation(address, vm))
            startProcessing()
          }
        }).rejected(object : Consumer<Throwable> {
          override fun consume(error: Throwable) {
            if (error !is ConnectException) {
              Promise.logError(CommandProcessor.LOG, error)
            }
            setState(ConnectionStatus.CONNECTION_FAILED, error.getMessage())
          }
        }).processed(object : Consumer<Vm> {
          override fun consume(vm: Vm) {
            connectCancelHandler.set(null)
          }
        })

        NettyUtil.connect(createBootstrap(address, result), address, connectionPromise, if (stopCondition == null) NettyUtil.DEFAULT_CONNECT_ATTEMPT_COUNT else -1, stopCondition)
      }
    })
    connectCancelHandler.set(object : Runnable {
      override fun run() {
        future.cancel(true)
      }
    })
  }

  protected open fun connectedAddressToPresentation(address: InetSocketAddress, vm: Vm): String {
    return address.getHostName() + ":" + address.getPort()
  }

  override fun detachAndClose(): Promise<Void> {
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
    return Promise.resolve(ContainerUtil.getFirstItem(targets))
  }
  else if (targets.isEmpty()) {
    return Promise.reject<T>("No tabs to inspect")
  }

  val result = AsyncPromise<T>()
  ApplicationManager.getApplication().invokeLater(object : Runnable {
    override fun run() {
      val list = JBList(targets)
      list.setCellRenderer(object : ColoredListCellRenderer<Any>() {
        override fun customizeCellRenderer(list: JList<*>, value: Any, index: Int, selected: Boolean, hasFocus: Boolean) {
          @suppress("UNCHECKED_CAST")
          append(itemToString(value as T))
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
              result.setError(Promise.createError("No target to inspect"))
            }
            else {
              result.setResult(value)
            }
          }
        })
        .createPopup()
        .showInFocusCenter()
    }
  })
  return result
}