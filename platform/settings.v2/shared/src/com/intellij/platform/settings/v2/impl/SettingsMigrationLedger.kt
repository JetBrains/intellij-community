// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/settings-migrations.spec.md

import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.settings.v2.SettingsContextFilters
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.SettingsMigration
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal
import java.time.Instant

/** Separates the plugin id from the migration id in [SettingsMigrationIdentity.ledgerEntryKey]. */
private const val LEDGER_ENTRY_KEY_SEPARATOR = ':'

/**
 * Identifies a [SettingsMigration] in a migration ledger. The `id` of a migration is unique only within its plugin, so
 * the identity also holds the plugin that declares the migration.
 *
 * @property pluginId the id of the plugin that declares the migration extension
 * @property migrationId the `id` attribute of the migration extension
 */
@Internal
data class SettingsMigrationIdentity(val pluginId: PluginId, val migrationId: String) {
  /** Returns the key of the ledger entry of this migration: `<pluginId>:<migrationId>`. */
  val ledgerEntryKey: String
    get() = "${pluginId.idString}$LEDGER_ENTRY_KEY_SEPARATOR$migrationId"
}

/**
 * Holds the migrations that ran with success in one scope: the application or one project. The platform runs a
 * [SettingsMigration] only when no ledger of the scope lists it (ADR 0170).
 *
 * @property completed the ISO-8601 time of the successful run of each migration, by its
 *   [ledger entry key][SettingsMigrationIdentity.ledgerEntryKey]
 */
@Internal
@Serializable
data class SettingsMigrationLedger(val completed: Map<String, String> = emptyMap()) {
  /** Returns whether this ledger lists [migration] as run. */
  fun isCompleted(migration: SettingsMigrationIdentity): Boolean = migration.ledgerEntryKey in completed

  /** Returns this ledger with [migration] recorded as run at [completedAt], which is the current time by default. */
  fun withCompleted(migration: SettingsMigrationIdentity, completedAt: Instant = Instant.now()): SettingsMigrationLedger =
    copy(completed = completed + (migration.ledgerEntryKey to completedAt.toString()))
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
  contextFilter = SettingsContextFilters.APP_ONLY,
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
  contextFilter = SettingsContextFilters.PROJECT_ONLY,
)
