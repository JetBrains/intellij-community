// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/settings-migrations.spec.md

import com.intellij.platform.settings.v2.SettingsContextKind
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.SettingsMigration
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal
import java.time.Instant

/**
 * Holds the migrations that ran with success in one scope: the application or one project. The platform runs a
 * [SettingsMigration] only when no ledger of the scope lists its id (ADR 0170).
 *
 * @property completed the ISO-8601 time of the successful run of each migration, by the id of the migration
 */
@Internal
@Serializable
data class SettingsMigrationLedger(val completed: Map<String, String> = emptyMap()) {
  /** Returns this ledger with the migration [id] recorded as run at [completedAt]. */
  fun withCompleted(id: String, completedAt: Instant): SettingsMigrationLedger = copy(completed = completed + (id to completedAt.toString()))
}

/**
 * Holds the ledger of the application migrations ([SettingsMigration.APP_EP_NAME]). Only the application context
 * accepts it, so a project never reads it up its chain. [PlatformKeyPromoter] registers it.
 */
@Internal
val APP_MIGRATION_LEDGER: SettingsKey<SettingsMigrationLedger> = SettingsKey(
  name = "settings.appMigrations",
  default = SettingsMigrationLedger(),
  serializer = kotlinxSerializer(),
  contextFilter = { it.kind == SettingsContextKind.APPLICATION },
)

/**
 * Holds the ledger of the project migrations ([SettingsMigration.PROJECT_EP_NAME]). Only a project context accepts
 * it, so a read never falls through to the application. [PlatformKeyPromoter] registers it.
 */
@Internal
val PROJECT_MIGRATION_LEDGER: SettingsKey<SettingsMigrationLedger> = SettingsKey(
  name = "settings.projectMigrations",
  default = SettingsMigrationLedger(),
  serializer = kotlinxSerializer(),
  contextFilter = { it.kind == SettingsContextKind.PROJECT },
)
