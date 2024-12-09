// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.project.asEntity
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import fleet.kernel.DurableEntityType
import fleet.kernel.change
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

private class XDebuggerValueLookupEntityTypesProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> {
    return listOf(
      XDebuggerValueLookupListeningStartedEntity,
      XDebuggerValueLookupHideHintsRequestEntity,
    )
  }
}

@ApiStatus.Internal
data class XDebuggerValueLookupListeningStartedEntity(override val eid: EID) : Entity {
  val projectEntity by Project

  companion object : DurableEntityType<XDebuggerValueLookupListeningStartedEntity>(
    XDebuggerValueLookupListeningStartedEntity::class.java.name,
    "com.intellij",
    ::XDebuggerValueLookupListeningStartedEntity
  ) {
    val Project = requiredRef<ProjectEntity>("project")
  }
}

@ApiStatus.Internal
data class XDebuggerValueLookupHideHintsRequestEntity(override val eid: EID) : Entity {
  val projectEntity by Project

  companion object : DurableEntityType<XDebuggerValueLookupHideHintsRequestEntity>(
    XDebuggerValueLookupHideHintsRequestEntity::class.java.name,
    "com.intellij",
    ::XDebuggerValueLookupHideHintsRequestEntity
  ) {
    val Project = requiredRef<ProjectEntity>("project")
  }
}

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ValueLookupManagerController(private val project: Project, private val cs: CoroutineScope) {
  private val listeningStarted = AtomicBoolean(false)

  /**
   * Starts [ValueLookupManager] listening for events (e.g. mouse movement) to trigger evaluation popups
   */
  fun startListening() {
    if (!listeningStarted.compareAndSet(false, true)) {
      return
    }
    cs.launch(Dispatchers.IO) {
      withKernel {
        val projectEntity = project.asEntity()
        change {
          shared {
            val alreadyExists = XDebuggerValueLookupListeningStartedEntity.all().any { it.projectEntity == projectEntity }
            if (!alreadyExists) {
              XDebuggerValueLookupListeningStartedEntity.new {
                it[XDebuggerValueLookupListeningStartedEntity.Project] = projectEntity
              }
            }
          }
        }
      }
    }
  }

  /**
   * Requests [ValueLookupManager] to hide current evaluation hints
   */
  fun hideHint() {
    cs.launch(Dispatchers.IO) {
      val projectEntity = project.asEntity()
      withKernel {
        change {
          shared {
            val projectEntity = projectEntity
            XDebuggerValueLookupHideHintsRequestEntity.new {
              it[XDebuggerValueLookupHideHintsRequestEntity.Project] = projectEntity
            }
          }
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ValueLookupManagerController = project.service<ValueLookupManagerController>()

    /**
     * @see XDebuggerUtil#disableValueLookup(Editor)
     */
    @JvmField
    val DISABLE_VALUE_LOOKUP = Key.create<Boolean>("DISABLE_VALUE_LOOKUP");
  }
}