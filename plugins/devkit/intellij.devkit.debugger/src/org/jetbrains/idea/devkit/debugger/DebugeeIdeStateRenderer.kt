// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.debugger

import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.ui.tree.ExtraDebugNodesProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.frame.*
import com.sun.jdi.BooleanValue
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import javax.swing.Icon

/**
 * @see com.intellij.openapi.application.ApplicationStateDebugSupport
 */
private const val SUPPORT_CLASS_FQN = "com.intellij.openapi.application.ApplicationStateDebugSupport"
private const val GET_STATE_METHOD_NAME = "getApplicationState"
private const val GET_STATE_METHOD_SIGNATURE = "()Lcom/intellij/openapi/application/ApplicationDebugState;"
private const val READ_ACTION_ALLOWED_FIELD_NAME = "readActionAllowed"
private const val WRITE_ACTION_ALLOWED_FIELD_NAME = "writeActionAllowed"


internal class DebugeeIdeStateRenderer : ExtraDebugNodesProvider {
  override fun addExtraNodes(evaluationContext: EvaluationContext, children: XValueChildrenList) {
    if (!Registry.`is`("devkit.debugger.show.ide.state")) return
    val supportClass = evaluationContext.debugProcess.findClass(evaluationContext, SUPPORT_CLASS_FQN, null) ?: return
    val getStateMethod = supportClass.methodsByName(GET_STATE_METHOD_NAME, GET_STATE_METHOD_SIGNATURE).singleOrNull() ?: return
    val state = evaluationContext.debugProcess.invokeMethod(evaluationContext, supportClass as ClassType, getStateMethod, emptyList<Value>()) as ObjectReference?
    if (state == null) return
    val stateClass = state.referenceType()

    val fieldValues = state.getValues(stateClass.allFields()).mapKeys { it.key.name() }

    val isReadActionAllowedValue = fieldValues[READ_ACTION_ALLOWED_FIELD_NAME]
    val isReadActionAllowed = (isReadActionAllowedValue as? BooleanValue)?.value()
    val isWriteActionAllowedValue = fieldValues[WRITE_ACTION_ALLOWED_FIELD_NAME]
    val isWriteActionAllowed = (isWriteActionAllowedValue as? BooleanValue)?.value()

    if (isReadActionAllowed == null && isWriteActionAllowed == null) return

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
        addChild(node, "debugger.read.allowed", isReadActionAllowed?.toString(), AllIcons.Actions.ShowReadAccess, isWriteActionAllowed == null)
        addChild(node, "debugger.write.allowed", isWriteActionAllowed?.toString(), AllIcons.Actions.ShowWriteAccess, true)
      }

      private fun addChild(node: XCompositeNode, nameKey: String, value: String?, icon: Icon, isLast: Boolean) {
        if (value == null) return
        node.addChildren(XValueChildrenList.singleton(DevKitDebuggerBundle.message(nameKey), object : XValue() {
          override fun canNavigateToSource(): Boolean = false
          override fun computePresentation(node: XValueNode, place: XValuePlace) {
            node.setPresentation(icon, null, value, false)
          }
        }), isLast)
      }

    })
  }
}
