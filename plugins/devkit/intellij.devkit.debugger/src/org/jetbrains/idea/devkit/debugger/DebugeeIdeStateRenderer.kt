// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.debugger

import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.jdi.StackFrameProxy
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.impl.wrapIncompatibleThreadStateException
import com.intellij.debugger.memory.utils.StackFrameItem
import com.intellij.debugger.ui.tree.ExtraDebugNodesProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList
import com.intellij.xdebugger.impl.frame.XFramesView
import com.sun.jdi.BooleanValue
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
private const val APPLICATION_IMPL_FQN = "com.intellij.openapi.application.impl.ApplicationImpl"
private const val COROUTINES_KT_FQN = "com.intellij.openapi.application.CoroutinesKt"
private const val ACTIONS_KT_FQN = "com.intellij.openapi.application.ActionsKt"

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
        node.setPresentation(AllIcons.Nodes.EnterpriseProject, null, value, true)
      }

      override fun computeChildren(node: XCompositeNode) {
        addChild(node, isReadActionAllowed, isRead = true, addLink = isWriteActionAllowed != true)
        addChild(node, isWriteActionAllowed, isRead = false, addLink = true)
        node.addChildren(XValueChildrenList.EMPTY, true)
      }

      private fun addChild(node: XCompositeNode, value: Boolean?, isRead: Boolean, addLink: Boolean) {
        if (value == null) return
        val nameKey = if (isRead) "debugger.read.allowed" else "debugger.write.allowed"
        val icon = if (isRead) AllIcons.Actions.ShowReadAccess else AllIcons.Actions.ShowWriteAccess

        node.addChildren(XValueChildrenList.singleton(DevKitDebuggerBundle.message(nameKey), object : XValue() {
          override fun canNavigateToSource(): Boolean = false
          override fun computePresentation(node: XValueNode, place: XValuePlace) {
            node.setPresentation(icon, null, value.toString(), false)
            if (value && addLink) {
              addLinkToLockAccess(isRead, node, evaluationContext)
            }
          }
        }), false)
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

private fun isLockAccessMethod(frame: StackFrameProxy, isRead: Boolean): Boolean {
  val location = frame.location()
  val className = location.declaringType().name()
  val methodName = location.method().name()
  return if (isRead) {
    isReadAccessCall(className, methodName)
  }
  else {
    isWriteAccessCall(className, methodName)
  }
}

private fun isReadAccessCall(className: String, methodName: String): Boolean =
  className == "com.intellij.openapi.application.impl.NonBlockingReadActionImpl" && methodName == "executeSynchronously"
  || (className == THREADING_SUPPORT_FQN || className == APPLICATION_IMPL_FQN) && isApplicationReadAccess(methodName)
  || className == ACTIONS_KT_FQN && methodName == "runReadAction"
  || (className == "com.intellij.openapi.application.ReadAction"
      && (methodName == "run" || methodName == "execute" || methodName == "compute" || methodName == "computeCancellable"))
  || (className == "com.intellij.openapi.application.WriteIntentReadAction"
      && (methodName == "run" || methodName == "compute"))
  || (className == COROUTINES_KT_FQN
      && (methodName == "readAction" || methodName == "smartReadAction" || methodName == "constrainedReadAction"
          || methodName == "readActionUndispatched" || methodName == "constrainedReadActionUndispatched"
          || methodName == "readActionBlocking" || methodName == "smartReadActionBlocking"
          || methodName == "constrainedReadActionBlocking" || methodName == "writeIntentReadAction"))

private fun isWriteAccessCall(className: String, methodName: String): Boolean =
  className == ACTIONS_KT_FQN && methodName == "runWriteAction"
  || (className == "com.intellij.openapi.application.WriteAction"
      && (methodName == "run" || methodName == "execute" || methodName == "compute" || methodName == "runAndWait" || methodName == "computeAndWait"))
  || (className == COROUTINES_KT_FQN
      && (methodName == "writeAction" || methodName == "readAndWriteAction" || methodName == "constrainedReadAndWriteAction"))
  || (className == THREADING_SUPPORT_FQN || className == APPLICATION_IMPL_FQN) && isApplicationWriteAccess(methodName)

private fun isApplicationReadAccess(methodName: String) =
  methodName == "runReadAction"
  || methodName == "runWriteIntentReadAction"
  || methodName == "tryRunReadAction"
  || methodName == "runIntendedWriteActionOnCurrentThread"

private fun isApplicationWriteAccess(methodName: String) =
  methodName == "runWriteAction"
  || methodName == "executeSuspendingWriteAction"
  || methodName == "runWriteActionWithNonCancellableProgressInDispatchThread"
  || methodName == "runWriteActionWithCancellableProgressInDispatchThread"

private fun addLinkToLockAccess(
  isRead: Boolean,
  node: XValueNode,
  evaluationContext: EvaluationContext,
) {
  val evalContext = evaluationContext as? EvaluationContextImpl ?: return
  node.setFullValueEvaluator(object : JavaValue.JavaFullValueEvaluator(DevKitDebuggerBundle.message("debugger.navigate.to.lock.access"), evalContext) {
    override fun isShowValuePopup() = false
    override fun evaluate(callback: XFullValueEvaluationCallback) {
      val suspendContext = evalContext.suspendContext
      suspendContext.coroutineScope.launch {
        navigateToStackFrame(suspendContext)
      }.invokeOnCompletion {
        callback.evaluated("")
      }
    }

    private suspend fun navigateToStackFrame(suspendContext: SuspendContextImpl) {
      val debugProcess = suspendContext.debugProcess
      val xDebugSession = debugProcess.session.xDebugSession as? XDebugSessionImpl ?: return
      val framesView = xDebugSession.sessionTab?.framesView ?: return
      val framesList = framesView.framesList
      val xFrames = framesList.model.items.filterIsInstance<XStackFrame>()

      val asyncFrameIndex = xFrames.indexOfFirst { it is StackFrameItem.CapturedStackFrame }
      val syncXFrames = xFrames.flattenJavaFrames(toIndex = indexOrEndOfList(asyncFrameIndex, xFrames))
      selectLockFrame(suspendContext, syncXFrames, isRead, framesList)
    }
  })
}

private suspend fun selectLockFrame(
  suspendContext: SuspendContextImpl,
  javaFrames: List<JavaStackFrame>,
  isRead: Boolean,
  framesList: XDebuggerFramesList,
) {
  val (targetXFrameIndex, _) = javaFrames.withIndex().reversed().firstOrNull { (_, frame) ->
    withDebugContext(suspendContext) {
      isLockAccessMethod(frame.stackFrameProxy, isRead)
    }
  } ?: return
  if (targetXFrameIndex < 0) return
  var targetXFrame: XStackFrame = javaFrames.getOrNull(targetXFrameIndex + 1) ?: return
  if (!framesList.model.contains(targetXFrame)) {
    val collapsedFrames = framesList.model.items.filterIsInstance<XFramesView.HiddenStackFramesItem>()
    targetXFrame = collapsedFrames.firstOrNull { it.getHiddenFrames().contains(targetXFrame) } ?: return
    if (!framesList.model.contains(targetXFrame)) return
  }
  withContext(Dispatchers.EDT) {
    framesList.selectFrame(targetXFrame)
  }
}

private fun indexOrEndOfList(index: Int, list: List<*>): Int = if (index < 0) list.size else index

private fun List<XStackFrame>.flattenJavaFrames(toIndex: Int = size) =
  subList(0, toIndex).flatMap { frame ->
    when (frame) {
      is JavaStackFrame -> listOf(frame)
      is XFramesView.HiddenStackFramesItem -> frame.getHiddenFrames().filterIsInstance<JavaStackFrame>()
      else -> emptyList()
    }
  }
