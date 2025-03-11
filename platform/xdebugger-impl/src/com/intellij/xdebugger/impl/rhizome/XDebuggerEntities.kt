// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rhizome

import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.project.ProjectEntity
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.impl.rhizome.XValueEntity.Companion.XValueId
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorId
import com.intellij.xdebugger.impl.rpc.XValueId
import com.jetbrains.rhizomedb.*
import fleet.kernel.change
import fleet.util.UID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

private class XDebuggerEntityTypesProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> {
    return listOf(
      XDebugSessionEntity,
      XDebuggerEvaluatorEntity,
      XValueEntity,
      XSuspendContextEntity,
      XExecutionStackEntity,
      XStackFrameEntity,
    )
  }
}

/**
 * Represents an entity which holds reference to the [XDebugSession].
 * Such an entity allows to send [XDebugSessionId] to a client and afterward find [XDebugSession] by this id.
 *
 * This entity cannot be shared between frontend and backend.
 * It should be used only on the backend side.
 *
 * @see [storeXDebugSessionInDb]
 */
data class XDebugSessionEntity(override val eid: EID) : Entity {
  val sessionId: XDebugSessionId by SessionId
  val session: XDebugSession by Session
  val projectEntity: ProjectEntity by ProjectEntity

  /**
   * Current evaluator of the [session].
   *
   * @see [com.intellij.xdebugger.impl.XDebugSessionCurrentStackFrameManager]
   */
  val evaluator: XDebuggerEvaluatorEntity? by Evaluator

  val currentSourcePosition: XSourcePosition? by CurrentSourcePosition

  companion object : EntityType<XDebugSessionEntity>(
    XDebugSessionEntity::class.java.name,
    "com.intellij.xdebugger.impl.rhizome",
    ::XDebugSessionEntity
  ) {
    val SessionId: Required<XDebugSessionId> = requiredTransient("sessionId", Indexing.UNIQUE)
    val Session: Required<XDebugSession> = requiredTransient("session", Indexing.UNIQUE)
    val ProjectEntity: Required<ProjectEntity> = requiredRef("project", RefFlags.CASCADE_DELETE_BY)

    val Evaluator: Optional<XDebuggerEvaluatorEntity> = optionalRef("evaluator")

    val CurrentSourcePosition: Optional<XSourcePosition> = optionalTransient("currentPosition")
  }
}

/**
 * Represents an entity which holds reference to the [XDebuggerEvaluator].
 * Such an entity allows to send [XDebuggerEvaluatorId] to a client and afterward find [XDebuggerEvaluator] by this id.
 *
 * The entity is attached to a session by reference to [XDebugSessionEntity].
 * So, the entity will be removed when [XDebugSession] is stopped.
 *
 * This entity cannot be shared between frontend and backend.
 * It should be used only on the backend side.
 *
 * @see [com.intellij.xdebugger.impl.XDebugSessionCurrentStackFrameManager]
 */
data class XDebuggerEvaluatorEntity(override val eid: EID) : Entity {
  val evaluatorId: XDebuggerEvaluatorId by EvaluatorId
  val evaluator: XDebuggerEvaluator by Evaluator
  val sessionEntity: XDebugSessionEntity by Session

  companion object : EntityType<XDebuggerEvaluatorEntity>(
    XDebuggerEvaluatorEntity::class.java.name,
    "com.intellij.xdebugger.impl.rhizome",
    ::XDebuggerEvaluatorEntity
  ) {
    val EvaluatorId: Required<XDebuggerEvaluatorId> = requiredTransient("evaluatorId", Indexing.UNIQUE)
    val Evaluator: Required<XDebuggerEvaluator> = requiredTransient("evaluator")
    val Session: Required<XDebugSessionEntity> = requiredRef<XDebugSessionEntity>("session", RefFlags.CASCADE_DELETE_BY)
  }
}

/**
 * Represents an entity which holds reference to the [XValue].
 * This entity allows sending [XValueId] to a client and afterward retrieving [XValue] by this id.
 *
 * The entity is attached to a session by reference to [XDebugSessionEntity].
 * So, the entity will be removed when the corresponding [XDebugSession] is stopped.
 *
 * This entity cannot be shared between frontend and backend.
 * It should be used only on the backend side.
 *
 * @see [com.intellij.platform.debugger.impl.backend.BackendXDebuggerEvaluatorApi]
 */
data class XValueEntity(override val eid: EID) : Entity {
  val xValueId: XValueId by XValueId
  val xValue: XValue by XValueAttribute
  val sessionEntity: XDebugSessionEntity by SessionEntity

  /**
   * Reference to a parent [XValue].
   * When [parentXValueEntity] is deleted, this [XValueEntity] also will be deleted.
   */
  val parentXValueEntity: XValueEntity? by ParentXValue

  val marker: XValueMarkerDto? by Marker

  val fullValueEvaluator: XFullValueEvaluator? by FullValueEvaluator

  companion object : EntityType<XValueEntity>(
    XValueEntity::class.java.name,
    "com.intellij.xdebugger.impl.rhizome",
    ::XValueEntity
  ) {
    val XValueId: Required<XValueId> = requiredTransient("xValueId", Indexing.UNIQUE)
    val XValueAttribute: Required<XValue> = requiredTransient("xValue")
    val SessionEntity: Required<XDebugSessionEntity> = requiredRef("sessionEntity", RefFlags.CASCADE_DELETE_BY)
    val ParentXValue: Optional<XValueEntity> = optionalRef<XValueEntity>("parentXValue", RefFlags.CASCADE_DELETE_BY)
    val FullValueEvaluator: Optional<XFullValueEvaluator> = optionalTransient("fullValueEvaluator")
    val Marker: Optional<XValueMarkerDto> = optionalTransient("marker")
  }
}

// TODO[IJPL-160146]: Implement implement Color serialization
@Serializable
data class XValueMarkerDto(val text: String, @Transient val color: Color? = null, val tooltipText: String?)

data class XSuspendContextEntity(override val eid: EID) : XDebuggerEntity<XSuspendContext> {

  companion object : EntityType<XSuspendContextEntity>(
    XSuspendContextEntity::class.java.name,
    "com.intellij.xdebugger.impl.rhizome",
    ::XSuspendContextEntity,
    XDebuggerEntity
  )
}

data class XExecutionStackEntity(override val eid: EID) : XDebuggerEntity<XExecutionStack> {

  companion object : EntityType<XExecutionStackEntity>(
    XExecutionStackEntity::class.java.name,
    "com.intellij.xdebugger.impl.rhizome",
    ::XExecutionStackEntity,
    XDebuggerEntity
  )
}

data class XStackFrameEntity(override val eid: EID) : XDebuggerEntity<XStackFrame> {

  companion object : EntityType<XStackFrameEntity>(
    XStackFrameEntity::class.java.name,
    "com.intellij.xdebugger.impl.rhizome",
    ::XStackFrameEntity,
    XDebuggerEntity
  )
}

@Suppress("UNCHECKED_CAST")
interface XDebuggerEntity<T : Any> : Entity {
  val id: UID
    get() = this[IdAttr]

  val obj: T
    get() = this[ObjAttr] as T

  companion object : Mixin<XDebuggerEntity<*>>(XDebuggerEntity::class.java.name, "com.intellij.xdebugger.impl.rhizome") {
    val IdAttr: Attributes<XDebuggerEntity<*>>.Required<UID> = requiredTransient<UID>("id", Indexing.UNIQUE)
    private val ObjAttr: Attributes<XDebuggerEntity<*>>.Required<Any> = requiredTransient<Any>("obj")

    @ApiStatus.Internal
    fun <T : Any, E : XDebuggerEntity<T>, ET : EntityType<E>> ET.new(changeScope: ChangeScope, obj: Any): E = with(changeScope) {
      new {
        it[IdAttr] = UID.random()
        it[ObjAttr] = obj
      }
    }

    @ApiStatus.Internal
    inline fun <reified E : XDebuggerEntity<*>> debuggerEntity(id: UID): E? {
      val entity = entity(IdAttr, id) ?: return null
      return entity as E
    }
  }
}

@Suppress("UnusedFlow")
@ApiStatus.Internal
fun <T : Any, E : XDebuggerEntity<T>, ET : EntityType<E>> Flow<T?>.withIDs(createEntity: ChangeScope.(T) -> E): Flow<Pair<T, UID>?> {
  val flow = this
  return channelFlow {
    flow.collectLatest { value ->
      if (value == null) {
        send(null)
        return@collectLatest
      }
      val valueEntity = change {
        createEntity(value)
      }
      try {
        send(value to valueEntity.id)
        awaitCancellation()
      }
      finally {
        withContext(NonCancellable) {
          change {
            valueEntity.delete()
          }
        }
      }
    }
  }
}
