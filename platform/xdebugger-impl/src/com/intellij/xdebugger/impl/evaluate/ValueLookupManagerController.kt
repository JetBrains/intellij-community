// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.project.asEntity
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import fleet.kernel.DurableEntityType
import fleet.kernel.change
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
class XDebuggerValueLookupListeningStartedEntity(override val eid: EID) : Entity {
  companion object : DurableEntityType<XDebuggerValueLookupListeningStartedEntity>(
    "XDebuggerValueLookupListeningStartedEntity",
    "com.intellij",
    ::XDebuggerValueLookupListeningStartedEntity
  ) {
    val Project = requiredRef<ProjectEntity>("project")
  }

  val projectEntity by Project
}

@ApiStatus.Internal
class XDebuggerValueLookupHideHintsRequestEntity(override val eid: EID) : Entity {
  companion object : DurableEntityType<XDebuggerValueLookupHideHintsRequestEntity>(
    "XDebuggerValueLookupHideHintsEntity",
    "com.intellij",
    ::XDebuggerValueLookupHideHintsRequestEntity
  ) {
    val Project = requiredRef<ProjectEntity>("project")
  }

  val projectEntity by Project
}

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ValueLookupManagerController(private val project: Project, private val cs: CoroutineScope) {
  private var listeningStarted = AtomicReference(false)

  /**
   * Starts [ValueLookupManager] listening for events (e.g. mouse movement) to trigger evaluation popups
   */
  fun startListening() {
    if (listeningStarted.get()) {
      return
    }
    cs.launch(Dispatchers.Main) {
      withKernel {
        change {
          shared {
            register(XDebuggerValueLookupListeningStartedEntity)
          }
        }
        val entities = XDebuggerValueLookupListeningStartedEntity.all()
        if (entities.isEmpty()) {
          change {
            shared {
              XDebuggerValueLookupListeningStartedEntity.new {
                it[XDebuggerValueLookupListeningStartedEntity.Project] = project.asEntity()
              }
            }
          }
        }
      }
      listeningStarted.set(true)
    }
  }

  /**
   * Requests [ValueLookupManager] to hide current evaluation hints
   */
  fun hideHint() {
    cs.launch(Dispatchers.Main) {
      withKernel {
        change {
          shared {
            register(XDebuggerValueLookupHideHintsRequestEntity)
            XDebuggerValueLookupHideHintsRequestEntity.new {
              it[XDebuggerValueLookupHideHintsRequestEntity.Project] = project.asEntity()
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