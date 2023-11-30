// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database

import com.intellij.ae.database.dbs.SqliteDatabaseMetadata
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import java.util.*

private val logger = logger<IdService>()

@Service(Service.Level.APP)
@State(name = "AEIdeId", storages = [Storage("aeIdeId.xml", roamingType = RoamingType.DISABLED)], reportStatistic = false)
class IdService : PersistentStateComponent<IdService.State> {
  data class State(
    var id: String = UUID.randomUUID().toString()
  )
  companion object {
    fun getInstance() = ApplicationManager.getApplication().service<IdService>()
  }

  val id get() = state.id

  val machineId by lazy { MachineIdManager.getAnonymizedMachineId("com.intellij.platform.ae.database", "salty") ?: "undefined" }

  fun getDatabaseId(metadata: SqliteDatabaseMetadata) = metadata.ideId

  val ideCode by lazy {
    ApplicationInfo.getInstance().build.productCode.ifBlank {
      if (ApplicationManager.getApplication().isUnitTestMode) "unittest"
      else {
        logger.warn("productCode is blank, will use 'unknown' value")
        "unknown"
      }
    }
  }

  private var myState = State()

  override fun getState() = myState

  override fun loadState(state: State) {
    myState = state
  }
}