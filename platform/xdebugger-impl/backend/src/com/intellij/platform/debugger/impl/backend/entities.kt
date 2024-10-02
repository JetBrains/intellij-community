// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.project.ProjectEntity
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint
import com.jetbrains.rhizomedb.*

private class BackendXDebuggerEntityTypesProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> {
    return listOf(
      LocalValueHintEntity,
      LocalHintXDebuggerEvaluatorEntity,
      LocalHintXValueEntity
    )
  }
}

internal data class LocalValueHintEntity(override val eid: EID) : Entity {
  val projectEntity by Project
  val hint by Hint

  companion object : EntityType<LocalValueHintEntity>(
    LocalValueHintEntity::class.java.name,
    "com.intellij",
    ::LocalValueHintEntity
  ) {
    val Project = requiredRef<ProjectEntity>("project")
    val Hint = requiredTransient<AbstractValueHint>("hint")
  }
}

internal data class LocalHintXDebuggerEvaluatorEntity(override val eid: EID) : XDebuggerEvaluatorEntity {
  companion object : EntityType<LocalHintXDebuggerEvaluatorEntity>(
    LocalHintXDebuggerEvaluatorEntity::class.java.name,
    "com.intellij",
    ::LocalHintXDebuggerEvaluatorEntity,
    XDebuggerEvaluatorEntity
  )
}

internal interface XDebuggerEvaluatorEntity : Entity {
  val projectEntity: ProjectEntity
    get() = this[Project]

  val evaluator: XDebuggerEvaluator
    get() = this[Evaluator]

  companion object : Mixin<XDebuggerEvaluatorEntity>(XDebuggerEvaluatorEntity::class) {
    val Project = requiredRef<ProjectEntity>("project")
    val Evaluator = requiredTransient<XDebuggerEvaluator>("evaluator")
  }
}

internal data class LocalHintXValueEntity(override val eid: EID) : Entity {
  val projectEntity by Project
  val xValue by XValue

  companion object : EntityType<LocalHintXValueEntity>(
    LocalHintXValueEntity::class.java.name,
    "com.intellij",
    ::LocalHintXValueEntity
  ) {
    val Project = requiredRef<ProjectEntity>("project")
    val XValue = requiredTransient<XValue>("xValue")
  }
}