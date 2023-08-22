// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.debugger

import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.xdebugger.frame.XValueChildrenList
import com.sun.jdi.ClassType
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

class GroovyStackFrame(descriptor: StackFrameDescriptorImpl, update: Boolean) : JavaStackFrame(descriptor, update) {

  override fun superBuildVariables(evaluationContext: EvaluationContextImpl, children: XValueChildrenList) {
    addClosureFields(evaluationContext, children)
    super.superBuildVariables(evaluationContext, children)
  }

  private fun addClosureFields(evaluationContext: EvaluationContextImpl, children: XValueChildrenList) {
    val nodeManager = evaluationContext.debugProcess.xdebugProcess?.nodeManager ?: return
    val objectReference = evaluationContext.frameProxy?.thisObject() ?: return
    val fields = collectClosureFields(objectReference)
    val xFields = fields.map { nodeManager.getFieldDescriptor(null, objectReference, it) }
    for (xField in xFields) {
      children.add(ContextUtil.createValue(evaluationContext, nodeManager, xField))
    }
  }

  private fun collectClosureFields(objectReference: ObjectReference): List<Field> {
    val refType = objectReference.referenceType() as? ClassType ?: return emptyList()
    if (refType.superclass()?.name() != GroovyCommonClassNames.GROOVY_LANG_CLOSURE) return emptyList()
    return refType.fields().filter { !it.isInternal() }
  }

  private fun Field.isInternal(): Boolean = this.name()?.contains("$") == true
}