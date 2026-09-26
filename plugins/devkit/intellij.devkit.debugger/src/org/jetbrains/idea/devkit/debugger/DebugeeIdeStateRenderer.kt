// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.debugger

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.jdi.StackFrameProxy
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.impl.wrapIncompatibleThreadStateException
import com.intellij.debugger.ui.tree.ExtraDebugNodesProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.sun.jdi.BooleanValue
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import org.jetbrains.idea.devkit.debugger.settings.DevKitDebuggerSettings

/**
 * @see com.intellij.ide.debug.ApplicationStateDebugSupport
 */
private const val SUPPORT_CLASS_FQN = "com.intellij.ide.debug.ApplicationStateDebugSupport"
private const val GET_STATE_METHOD_NAME = "getApplicationState"
private const val GET_STATE_METHOD_SIGNATURE = "()Lcom/intellij/ide/debug/ApplicationDebugState;"
private const val READ_ACTION_ALLOWED_FIELD_NAME = "readActionAllowed"
private const val WRITE_ACTION_ALLOWED_FIELD_NAME = "writeActionAllowed"
private const val THREADING_SUPPORT_FQN = "com.intellij.openapi.application.impl.NestedLocksThreadingSupport"

internal data class IdeState(val readAllowed: Boolean?, val writeAllowed: Boolean?)

internal fun getIdeState(evaluationContext: EvaluationContext): IdeState? = try {
  wrapIncompatibleThreadStateException {
    val suspendContext = evaluationContext.suspendContext as? SuspendContextImpl ?: return null
    val supportClass = findClassOrNull(suspendContext, SUPPORT_CLASS_FQN) as? ClassType ?: return null
    val debugProcess = evaluationContext.debugProcess as? DebugProcessImpl ?: return null
    if (!debugProcess.isEvaluationPossible(suspendContext)) return null
    val state = evaluationContext.computeAndKeep {
      DebuggerUtilsImpl.invokeClassMethod(evaluationContext, supportClass, GET_STATE_METHOD_NAME, GET_STATE_METHOD_SIGNATURE, emptyList()) as? ObjectReference
    } ?: return null

    val stateClass = state.referenceType()
    val fieldValues = state.getValues(stateClass.allFields()).mapKeys { it.key.name() }

    val readField = (fieldValues[READ_ACTION_ALLOWED_FIELD_NAME] as? BooleanValue)?.value()
    val writeField = (fieldValues[WRITE_ACTION_ALLOWED_FIELD_NAME] as? BooleanValue)?.value()
    IdeState(readAllowed = readField, writeAllowed = writeField)
  }
}
catch (e: Exception) {
  DebuggerUtilsImpl.logError(e)
  null
}


internal class DebugeeIdeStateRenderer : ExtraDebugNodesProvider {
  override fun addExtraNodes(evaluationContext: EvaluationContext, children: XValueChildrenList) {
    if (!DevKitDebuggerSettings.getInstance().showIdeState) return
    if (!Registry.`is`("devkit.debugger.show.ide.state")) return
    val ideState = getIdeState(evaluationContext) ?: return
    if (ideState.readAllowed == null && ideState.writeAllowed == null) return

    val (isReadActionAllowed, isWriteActionAllowed) = try {
      wrapIncompatibleThreadStateException {
        (ideState.readAllowed to ideState.writeAllowed).adjustLockStatus(evaluationContext)
      } ?: return
    }
    catch (e: EvaluateException) {
      DebuggerUtilsImpl.logError(e)
      return
    }

    fun icon(isAvailable: Boolean) = if (isAvailable) "✓" else "✗"
    children.addTopValue(object : XNamedValue(DevKitDebuggerBundle.message("debugger.ide.state")) {
      override fun canNavigateToSource(): Boolean = false
      override fun computePresentation(node: XValueNode, place: XValuePlace) {
        val readState = if (isReadActionAllowed != null) DevKitDebuggerBundle.message("debugger.read.state", icon(isReadActionAllowed)) else null
        val writeState = if (isWriteActionAllowed != null) DevKitDebuggerBundle.message("debugger.write.state", icon(isWriteActionAllowed)) else null
        val value = listOfNotNull(readState, writeState).joinToString(separator = " ")
        node.setPresentation(AllIcons.Nodes.EnterpriseProject, null, value, false)
      }
    })
  }
}

/**
 * The read/write lock status that we can access with [com.intellij.ide.debug.ApplicationStateDebugSupport]
 * is valid only for the top frame.
 * While in the deeper frames, we should adjust the lock status based on the read/write lock method calls and the current frame.
 */
private fun Pair<Boolean?, Boolean?>.adjustLockStatus(evaluationContext: EvaluationContext): Pair<Boolean?, Boolean?> {
  var globalRead = first
  var globalWrite = second
  if (globalRead != true && globalWrite != true) return this

  val stackBased = getReadWriteAccessStateBasedOnCurrentFrame(evaluationContext) ?: return this
  val (readAllowed, writeAllowed) = stackBased
  if (readAllowed != null && globalRead == true) {
    globalRead = readAllowed
  }
  if (writeAllowed != null && globalWrite == true) {
    globalWrite = writeAllowed
  }
  return globalRead to globalWrite
}

private fun getReadWriteAccessStateBasedOnCurrentFrame(evaluationContext: EvaluationContext): Pair<Boolean?, Boolean?>? {
  val currentXFrame = (evaluationContext.suspendContext as SuspendContextImpl).debugProcess.session.xDebugSession?.currentStackFrame
  if (currentXFrame !is JavaStackFrame) return null
  val currentFrame = currentXFrame.stackFrameProxy
  // No need to adjust as the first frame is always correct
  if (currentFrame.frameIndex == 0) return null

  val thread = evaluationContext.suspendContext.thread ?: return null
  val frames = List(thread.frameCount()) { thread.frame(it) }
  val currentIndex = frames.indexOf(currentFrame)
  if (currentIndex < 0) return null

  val (readIndex, writeIndex) = findLockAccessIndex(frames)

  // Do not perform any modifications if we cannot find the call in the whole stack trace
  return Pair(if (readIndex == -1) null else currentIndex <= readIndex,
              if (writeIndex == -1) null else currentIndex <= writeIndex
  )
}

private fun findLockAccessIndex(frames: List<StackFrameProxy>): Pair<Int, Int> {
  var readIndex = -1
  var writeIndex = -1
  for (i in frames.indices) {
    val frame = frames[i]
    val location = frame.location()
    val className = location.declaringType().name()
    val methodName = location.method().name()
    val signature = location.method().signature()
    if (readIndex == -1) {
      if (className == THREADING_SUPPORT_FQN
          && ((methodName == "runReadAction"
               && signature == "(Ljava/lang/Class;Lcom/intellij/openapi/util/ThrowableComputable;)Ljava/lang/Object;")
              || (methodName == "runWriteIntentReadAction"
                  && signature == "(Lcom/intellij/openapi/util/ThrowableComputable;)Ljava/lang/Object;")
              || methodName == "tryRunReadAction" && signature == "(Ljava/lang/Runnable;)Z"
             )) {
        readIndex = i
      }
    }
    if (writeIndex == -1) {
      if (className == THREADING_SUPPORT_FQN
          && (methodName == "runWriteAction"
              && signature == "(Ljava/lang/Class;Lcom/intellij/openapi/util/ThrowableComputable;)Ljava/lang/Object;")) {
        writeIndex = i
      }
    }
    if (readIndex != -1 && writeIndex != -1) break
  }
  return readIndex to writeIndex
}

