// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLightLineBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.util.containers.MultiMap
import com.intellij.xdebugger.breakpoints.XBreakpoint
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

private val log = logger<XLineBreakpointManager>()

@ApiStatus.Internal
class XLineBreakpointManager(
  internal val project: Project,
  coroutineScope: CoroutineScope,
  isEnabled: Boolean,
  private val manager: XBreakpointManagerProxy,
): XLineBreakpointManagerProxy {
  private val myBreakpoints = MultiMap.createConcurrent<String, XLineBreakpointProxy>()
  private val visualizationManager: XLineBreakpointVisualizationManager? = if (isEnabled) {
    XLineBreakpointVisualizationManager(project, coroutineScope, manager = this)
  }
  else {
    null
  }

  @ApiStatus.Internal
  fun onFileDeleted(url: String) {
    removeBreakpoints(myBreakpoints[url])
  }

  @ApiStatus.Internal
  fun onFileUrlChanged(oldUrl: String, newUrl: String) {
    myBreakpoints.values().forEach { breakpoint ->
      val url = breakpoint.getFile()?.url ?: breakpoint.getFileUrl()
      if (FileUtil.startsWith(url, oldUrl)) {
        breakpoint.setFileUrl(newUrl + url.substring(oldUrl.length))
      }
    }
  }

  fun registerBreakpoint(breakpoint: XLineBreakpointProxy, initUI: Boolean) {
    if (initUI) {
      visualizationManager?.updateBreakpointNow(breakpoint)
    }
    val fileUrl = breakpoint.getFile()?.url ?: breakpoint.getFileUrl()
    log.debug { "Register line breakpoint ${breakpoint.id} ${breakpoint.javaClass.simpleName}: $fileUrl" }
    myBreakpoints.putValue(fileUrl, breakpoint)
  }

  fun unregisterBreakpoint(breakpoint: XLineBreakpointProxy) {
    val fileUrl = breakpoint.getFile()?.url ?: breakpoint.getFileUrl()
    val removed = myBreakpoints.remove(fileUrl, breakpoint)
    log.debug { "Unregister line breakpoint ${breakpoint.id} [removed=$removed] ${breakpoint.javaClass.simpleName}: $fileUrl" }
  }

  override fun getDocumentBreakpointProxies(document: Document): Collection<XLineBreakpointProxy> {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return emptyList()
    return myBreakpoints[file.url]
  }

  override fun getAllBreakpoints(): Collection<XLineBreakpointProxy> {
    return myBreakpoints.values()
  }

  internal fun getBreakpointFileUrls(): Collection<String> {
    return myBreakpoints.keySet()
  }

  internal fun getFileBreakpoints(fileUrl: String): Collection<XLineBreakpointProxy> {
    return myBreakpoints[fileUrl]
  }

  internal fun removeBreakpoints(toRemove: Collection<XBreakpointProxy>?) {
    for (breakpoint in toRemove.orEmpty()) {
      manager.removeBreakpoint(breakpoint)
    }
  }

  override fun breakpointChanged(breakpoint: XLightLineBreakpointProxy) {
    visualizationManager?.breakpointChanged(breakpoint)
  }

  @JvmOverloads
  fun queueBreakpointUpdate(slave: XBreakpoint<*>?, callOnUpdate: Runnable? = null) {
    visualizationManager?.queueBreakpointUpdate(slave, callOnUpdate)
  }

  fun queueAllBreakpointsUpdate() {
    visualizationManager?.queueAllBreakpointsUpdate()
  }

  companion object {
    @JvmField
    val BREAKPOINT_LINE_KEY: DataKey<Int> = DataKey.create("xdebugger.breakpoint.line")
    @JvmField
    val INTER_LINE_BREAKPOINT_KEY: DataKey<Boolean> = DataKey.create("xdebugger.breakpoint.interline")
    @JvmField
    val LOG_EXPRESSION: Key<String> = Key.create("xdebugger.breakpoint.logExpression")
  }
}
