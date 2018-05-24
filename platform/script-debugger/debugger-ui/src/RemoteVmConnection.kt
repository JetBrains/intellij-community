// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.connection

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Conditions
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.connectRetrying
import com.intellij.util.io.socketConnection.ConnectionStatus
import io.netty.bootstrap.Bootstrap
import org.jetbrains.concurrency.*
import org.jetbrains.debugger.Vm
import org.jetbrains.io.NettyUtil
import org.jetbrains.rpc.LOG
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import javax.swing.JList

abstract class RemoteVmConnection<VmT : Vm> : VmConnection<VmT>() {

  var address: InetSocketAddress? = null

  private val connectCancelHandler = AtomicReference<() -> Unit>()

  abstract fun createBootstrap(address: InetSocketAddress, vmResult: AsyncPromise<VmT>): Bootstrap

  @JvmOverloads
  fun open(address: InetSocketAddress, stopCondition: Condition<Void>? = null): Promise<VmT> {
    if (address.isUnresolved) {
      val error = "Host ${address.hostString} is unresolved"
      setState(ConnectionStatus.CONNECTION_FAILED, error)
      return rejectedPromise(error)
    }

    this.address = address
    setState(ConnectionStatus.WAITING_FOR_CONNECTION, "Connecting to ${address.hostString}:${address.port}")

    val result = AsyncPromise<VmT>()
    result
      .onSuccess {
        connectionSucceeded(it, address)
      }
      .onError {
        if (it !is ConnectException) {
          LOG.errorIfNotMessage(it)
        }
        setState(ConnectionStatus.CONNECTION_FAILED, it.message)
      }
      .onProcessed {
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

  protected fun connectionSucceeded(it: VmT, address: InetSocketAddress) {
    vm = it
    setState(ConnectionStatus.CONNECTED, "Connected to ${connectedAddressToPresentation(address, it)}")
    startProcessing()
  }

  protected open fun doOpen(result: AsyncPromise<VmT>, address: InetSocketAddress, stopCondition: Condition<Void>?) {
    val maxAttemptCount = if (stopCondition == null) NettyUtil.DEFAULT_CONNECT_ATTEMPT_COUNT else -1
    val resultRejected = Condition<Void> { result.state == Promise.State.REJECTED }
    val combinedCondition = Conditions.or(stopCondition ?: Conditions.alwaysFalse(), resultRejected)
    val connectResult = createBootstrap(address, result).connectRetrying(address, maxAttemptCount, combinedCondition)
    connectResult.handleError(Consumer { result.setError(it) })
    connectResult.handleThrowable(Consumer { result.setError(it) })
    val channel = connectResult.channel
    channel?.closeFuture()?.addListener {
      if (result.isSucceeded) {
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

fun RemoteVmConnection<*>.open(address: InetSocketAddress, processHandler: ProcessHandler): Promise<out Vm> = open(address, Condition<java.lang.Void> { processHandler.isProcessTerminating || processHandler.isProcessTerminated })

fun <T> chooseDebuggee(targets: Collection<T>, selectedIndex: Int, renderer: (T, ColoredListCellRenderer<*>) -> Unit): Promise<T> {
  if (targets.size == 1) {
    return resolvedPromise(targets.first())
  }
  else if (targets.isEmpty()) {
    return rejectedPromise("No tabs to inspect")
  }

  val result = org.jetbrains.concurrency.AsyncPromise<T>()
  ApplicationManager.getApplication().invokeLater {
    val model = ContainerUtil.newArrayList(targets)
    val builder = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(model)
      .setRenderer(
        object : ColoredListCellRenderer<T>() {
          override fun customizeCellRenderer(list: JList<out T>, value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
            renderer(value, this)
          }
        })
      .setTitle("Choose Page to Debug")
      .setCancelOnWindowDeactivation(false)
      .setItemChosenCallback { value ->
        result.setResult(value)
      }
    if (selectedIndex != -1) {
      builder.setSelectedValue(model[selectedIndex], false)
    }
    builder
      .createPopup()
      .showInFocusCenter()
  }
  return result
}

@Throws(ExecutionException::class)
fun initRemoteVmConnectionSync(connection: RemoteVmConnection<*>, debugPort: Int): Vm {
  val address = InetSocketAddress(InetAddress.getLoopbackAddress(), debugPort)
  val vmPromise = connection.open(address)
  val vm: Vm
  try {
    vm = vmPromise.blockingGet(30, TimeUnit.SECONDS)!!
  }
  catch (e: Exception) {
    throw ExecutionException("Cannot connect to VM ($address)", e)
  }

  return vm
}