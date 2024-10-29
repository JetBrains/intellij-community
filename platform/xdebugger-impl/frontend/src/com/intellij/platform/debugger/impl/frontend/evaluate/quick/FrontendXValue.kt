// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.ConcurrencyUtil
import com.intellij.xdebugger.Obsolescent
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.evaluate.quick.HintXValue
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorApi
import com.intellij.xdebugger.impl.rpc.XValueComputeChildrenEvent
import com.intellij.xdebugger.impl.rpc.XValueId
import kotlinx.coroutines.*

internal class FrontendXValue(private val project: Project, private val xValueId: XValueId) : XValue(), HintXValue {
  override fun computePresentation(node: XValueNode, place: XValuePlace) {
    node.childCoroutineScope("FrontendXValue#computePresentation").launch(Dispatchers.EDT) {
      XDebuggerEvaluatorApi.getInstance().computePresentation(xValueId)?.collect { presentation ->
        // TODO[IJPL-160146]: pass proper params
        node.setPresentation(null, null, presentation.value, presentation.hasChildren)
      }
    }
  }

  override fun computeChildren(node: XCompositeNode) {
    node.childCoroutineScope("FrontendXValue#computeChildren").launch(Dispatchers.EDT) {
      XDebuggerEvaluatorApi.getInstance().computeChildren(xValueId)?.collect { computeChildrenEvent ->
        when (computeChildrenEvent) {
          is XValueComputeChildrenEvent.AddChildren -> {
            val childrenList = XValueChildrenList()
            for (i in computeChildrenEvent.children.indices) {
              childrenList.add(computeChildrenEvent.names[i], FrontendXValue(project, computeChildrenEvent.children[i]))
            }
            node.addChildren(childrenList, computeChildrenEvent.isLast)
          }
        }
      }
    }
  }

  override fun dispose() {
    project.service<FrontendXValueDisposer>().dispose(xValueId)
  }
}

@OptIn(DelicateCoroutinesApi::class)
private fun Obsolescent.childCoroutineScope(name: String): CoroutineScope {
  val obsolescent = this
  val scope = GlobalScope.childScope(name)
  scope.launch(context = Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
    while (!obsolescent.isObsolete) {
      delay(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)
    }
    scope.cancel()
  }
  return scope
}

@Service(Service.Level.PROJECT)
private class FrontendXValueDisposer(project: Project, val cs: CoroutineScope) {
  fun dispose(xValueId: XValueId) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        XDebuggerEvaluatorApi.getInstance().disposeXValue(xValueId)
      }
    }
  }
}