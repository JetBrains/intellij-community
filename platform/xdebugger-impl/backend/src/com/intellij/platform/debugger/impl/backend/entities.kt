// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.project.ProjectEntity
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebuggerActiveSessionEntity
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import com.jetbrains.rhizomedb.RefFlags
import org.jetbrains.annotations.ApiStatus

private class BackendXDebuggerEntityTypesProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> {
    return listOf(
      LocalValueHintEntity,
      LocalHintXValueEntity,
      LocalXDebuggerSessionEvaluatorEntity,
    )
  }
}

internal data class LocalValueHintEntity(override val eid: EID) : Entity {
  val projectEntity by Project
  val hint by Hint

  @ApiStatus.Internal
  companion object : EntityType<LocalValueHintEntity>(
    LocalValueHintEntity::class.java.name,
    "com.intellij",
    ::LocalValueHintEntity
  ) {
    val Project = requiredRef<ProjectEntity>("project")
    val Hint = requiredTransient<AbstractValueHint>("hint")
  }
}

internal data class LocalHintXValueEntity(override val eid: EID) : Entity {
  val projectEntity by Project
  val xValue by XValue
  val parentXValue by ParentXValue

  @ApiStatus.Internal
  companion object : EntityType<LocalHintXValueEntity>(
    LocalHintXValueEntity::class.java.name,
    "com.intellij",
    ::LocalHintXValueEntity
  ) {
    val Project = requiredRef<ProjectEntity>("project")
    val XValue = requiredTransient<XValue>("xValue")
    val ParentXValue = optionalRef<LocalHintXValueEntity>("parentXValue", RefFlags.CASCADE_DELETE_BY)
  }
}

internal data class LocalXDebuggerSessionEvaluatorEntity(override val eid: EID) : Entity {
  val sessionEntity by Session
  val evaluator by Evaluator

  @ApiStatus.Internal
  companion object : EntityType<LocalXDebuggerSessionEvaluatorEntity>(
    LocalXDebuggerSessionEvaluatorEntity::class.java.name,
    "com.intellij",
    ::LocalXDebuggerSessionEvaluatorEntity
  ) {
    val Session = requiredRef<XDebuggerActiveSessionEntity>("session", RefFlags.UNIQUE)
    val Evaluator = requiredTransient<XDebuggerEvaluator>("evaluator")
  }
}