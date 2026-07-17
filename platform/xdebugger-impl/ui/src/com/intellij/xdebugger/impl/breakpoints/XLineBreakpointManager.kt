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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

private val log = logger<XLineBreakpointManager>()

@ApiStatus.Internal
class XLineBreakpointManager(
  internal val project: Project,
  private val manager: XBreakpointManagerProxy,
): XLineBreakpointManagerProxy {
  private val myBreakpointsById = ConcurrentHashMap<XBreakpointId, XLineBreakpointProxy>()
  private val myBreakpointsByFile = MultiMap.createConcurrent<VirtualFile, XLineBreakpointProxy>()

  fun onFileDeleted(file: VirtualFile) {
    removeBreakpoints(getFileBreakpoints(file))
  }

  fun onFileUrlChanged(oldUrl: String, newUrl: String) {
    getAllBreakpoints().forEach { breakpoint ->
      val url = breakpoint.getFileUrl()
      if (FileUtil.startsWith(url, oldUrl)) {
        breakpoint.setFileUrl(newUrl + url.substring(oldUrl.length))
      }
    }
  }

  fun registerBreakpoint(breakpoint: XLineBreakpointProxy) {
    myBreakpointsById[breakpoint.id] = breakpoint
    log.debug { "Register line breakpoint ${breakpoint.id} ${breakpoint.javaClass.simpleName}: ${breakpoint.getFileUrl()}" }
    val file = breakpoint.getFile()
    if (file != null) {
      myBreakpointsByFile.putValue(file, breakpoint)
    }
    else {
      log.warn("Breakpoint(${breakpoint.id}) file is not found during registration: ${breakpoint.getFileUrl()}")
    }
  }

  fun unregisterBreakpoint(breakpoint: XLineBreakpointProxy) {
    val removed = myBreakpointsById.remove(breakpoint.id) != null
    val removedByFile = breakpoint.getFile()?.let { myBreakpointsByFile.remove(it, breakpoint) } ?: false
    if (removed != removedByFile) {
      val associatedFile = myBreakpointsByFile.entrySet().firstOrNull { it.value == breakpoint }?.key
      if (associatedFile != null) {
        myBreakpointsByFile.remove(associatedFile, breakpoint)
      }
    }
    log.debug { "Unregister line breakpoint ${breakpoint.id} [removed=$removed] ${breakpoint.javaClass.simpleName}: ${breakpoint.getFileUrl()}" }
  }

  override fun getDocumentBreakpointProxies(document: Document): Collection<XLineBreakpointProxy> {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return emptyList()
    return getFileBreakpoints(file)
  }

  override fun getAllBreakpoints(): Collection<XLineBreakpointProxy> {
    return myBreakpointsById.values
  }

  fun getFileBreakpoints(file: VirtualFile): Collection<XLineBreakpointProxy> {
    return myBreakpointsByFile[file]
  }

  fun removeBreakpoints(toRemove: Collection<XBreakpointProxy>?) {
    for (breakpoint in toRemove.orEmpty()) {
      manager.removeBreakpoint(breakpoint)
    }
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
