// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/settings-migrations.spec.md

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.LazyExtension
import com.intellij.openapi.extensions.useOrLogError
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.settings.v2.ProjectAware
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsContextKind
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.SettingsMigration
import com.intellij.platform.settings.v2.SettingsVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly

/**
 * Runs the [SettingsMigration]s that the migration ledger of a scope does not list, and records each one that ends
 * with success (ADR 0170). [SettingsVaultImpl] owns it, calls [watchExtensionPoints] once, and calls [layersInstalled]
 * from its reload.
 *
 * - A check reads only the ids of the extensions. It creates the instance of a migration only to run it (ADR 0168).
 * - Each migration runs in its own coroutine, with no order between them. A migration does not hold a reload, and a
 *   write does not wait for it (ADR 0171).
 * - A check merges the ledgers of all localities. An update writes the closest one (ADR 0172).
 * - Two checks close together can launch one migration twice at the same time, which is accepted: a migration is
 *   fast, and a repeated run gives a correct result.
 * - In the unit-test mode, a reload and a change of the extensions start no check. A unit test of the engine starts
 *   a check with [runMigrationCheck].
 *
 * @param applicationCoroutineScope the coroutine scope of the application migrations and of the extension point listeners
 * @param vault the vault that owns this runner. The runner gets it directly, because the vault service is not
 *   registered yet when the vault creates the runner.
 */
internal class SettingsMigrationRunner(private val applicationCoroutineScope: CoroutineScope, private val vault: SettingsVault) {
  /**
   * Serializes the ledger updates of [recordCompleted]. An update reads the ledger and writes it back, so two updates
   * must not interleave, or one of them loses the entry of the other.
   */
  private val ledgerMutex = Mutex()

  /**
   * Starts the migration check of each loaded scope context after each change of a migration extension point. The
   * vault calls it at the end of its `init`, so no listener sees a vault that is not fully built. The listeners end
   * with [applicationCoroutineScope].
   */
  fun watchExtensionPoints() {
    for (migrationKind in MigrationKind.entries) {
      // A change listener gets no extension instance, so the load of a plugin creates no migration.
      migrationKind.extensionPoint.addChangeListener(applicationCoroutineScope) {
        migrationKind.loadedScopeContexts().forEach { startCheck(migrationKind, it) }
      }
    }
  }

  /**
   * Starts the migration check of each scope context of [installedKind] that [reloadScopeContext] covers. The vault
   * calls it after a reload of [reloadScopeContext] installs the layers of [installedKind] (ADR 0160).
   */
  fun layersInstalled(reloadScopeContext: SettingsContext, installedKind: SettingsContextKind) {
    val migrationKind = MigrationKind.entries.find { it.contextKind == installedKind } ?: return
    for (scopeContext in migrationKind.loadedScopeContexts()) {
      if (reloadScopeContext.isAncestorOf(scopeContext, strict = false)) startCheck(migrationKind, scopeContext)
    }
  }

  /**
   * Starts the [check] of [scopeContext] in the coroutine scope of its target, so the migrations of a project stop
   * when the project closes. In the unit-test mode it starts nothing.
   */
  private fun startCheck(migrationKind: MigrationKind, scopeContext: SettingsContext) {
    if (ApplicationManager.getApplication().isUnitTestMode) return
    val migrationsCoroutineScope = migrationsCoroutineScopeOf(scopeContext) ?: return
    migrationsCoroutineScope.launch { check(migrationKind, scopeContext, migrationsCoroutineScope) }
  }

  /**
   * Runs the check of [scopeContext], as a reload does outside the unit-test mode, and returns when the check has
   * launched the pending migrations. It does not wait for their end.
   *
   * @throws IllegalStateException when [scopeContext] is of a kind that has no migrations, or its project is not open
   */
  @TestOnly
  suspend fun checkByHand(scopeContext: SettingsContext) {
    val migrationKind = checkNotNull(MigrationKind.entries.find { it.contextKind == scopeContext.kind }) {
      "The context '${scopeContext.identity}' is of a kind that has no migrations"
    }
    val migrationsCoroutineScope = checkNotNull(migrationsCoroutineScopeOf(scopeContext)) {
      "The project of '${scopeContext.identity}' is not open"
    }
    check(migrationKind, scopeContext, migrationsCoroutineScope)
  }

  /**
   * Waits until [scopeContext] is ready, so the check reads a loaded ledger. Then launches the pending migrations of
   * [migrationKind] in [migrationsCoroutineScope]. See [launchPendingMigrations].
   */
  private suspend fun check(migrationKind: MigrationKind, scopeContext: SettingsContext, migrationsCoroutineScope: CoroutineScope) {
    vault.awaitReady(scopeContext)
    launchPendingMigrations(migrationsCoroutineScope, migrationKind, scopeContext)
  }

  /**
   * Returns the coroutine scope that runs the migrations of [scopeContext], or `null` when its project is not open.
   * The `when` has no `else` branch, so a new context kind does not get the coroutine scope of another kind by default.
   *
   * @throws IllegalStateException when [scopeContext] is of a kind that has no migrations
   */
  private fun migrationsCoroutineScopeOf(scopeContext: SettingsContext): CoroutineScope? = when (scopeContext.kind) {
    SettingsContextKind.APPLICATION -> applicationCoroutineScope
    SettingsContextKind.PROJECT ->
      (scopeContext as ProjectAware).project?.takeUnless { it.isDisposed }?.service<ProjectMigrationsCoroutineScope>()?.coroutineScope
    SettingsContextKind.FILE -> error("A migration has no coroutine scope at the file context '${scopeContext.identity}'")
  }

  /**
   * Launches each migration of [migrationKind] that no ledger of [scopeContext] lists. A migration with no id never
   * runs, and the check logs an error for it. The ledger read takes no lock: a vault read sees a whole ledger, before
   * or after an update.
   *
   * @param migrationsCoroutineScope the supervisor coroutine scope of the runs. A run is not a child of the check, so a
   *   failed run does not cancel the other runs.
   */
  private fun launchPendingMigrations(
    migrationsCoroutineScope: CoroutineScope,
    migrationKind: MigrationKind,
    scopeContext: SettingsContext,
  ) {
    val ledgers = migrationKind.ledger.values(scopeContext)
    for (extension in migrationKind.extensionPoint.filterableLazySequence()) {
      val pluginId = extension.pluginDescriptor.pluginId
      val id = extension.id
      if (id == null) {
        LOG.error(PluginException("The settings migration '${extension.implementationClassName}' has no id, so it never runs", pluginId))
        continue
      }
      val migration = SettingsMigrationIdentity(pluginId, id)
      if (ledgers.none { it.isCompleted(migration) }) {
        migrationsCoroutineScope.launch { runMigration(migrationKind, scopeContext, extension, migration) }
      }
    }
  }

  /**
   * Creates the migration of [extension], runs it for [scopeContext], and records it when it returns. A migration
   * that throws is logged with its plugin and is not recorded. A cancellation goes on and records nothing.
   */
  private suspend fun runMigration(
    migrationKind: MigrationKind,
    scopeContext: SettingsContext,
    extension: LazyExtension<SettingsMigration>,
    migration: SettingsMigrationIdentity,
  ) {
    val isCompleted = extension.useOrLogError { instance ->
      instance.migrate(scopeContext)
      true
    }
    if (isCompleted == true) recordCompleted(migrationKind.ledger, scopeContext, migration)
  }

  /**
   * Adds [migration] to the closest ledger of [scopeContext], local over team, and writes it back by the usual write
   * rules (ADR 0172). So the first update of a locality copies the entries of the closest ledger into it.
   */
  private suspend fun recordCompleted(
    ledger: SettingsKey<SettingsMigrationLedger>,
    scopeContext: SettingsContext,
    migration: SettingsMigrationIdentity,
  ) {
    ledgerMutex.withLock {
      ledger.set(scopeContext, ledger[scopeContext].withCompleted(migration))
    }
  }

  /**
   * Tells the two kinds of migration: once per application, and once per project (ADR 0168).
   *
   * @property contextKind the kind of the scope contexts of the migrations
   * @property extensionPoint the extension point that declares the migrations
   * @property ledger the key of the ledger of the migrations, which only a context of [contextKind] accepts
   */
  private enum class MigrationKind(
    val contextKind: SettingsContextKind,
    val extensionPoint: ExtensionPointName<SettingsMigration>,
    val ledger: SettingsKey<SettingsMigrationLedger>,
  ) {
    APPLICATION(SettingsContextKind.APPLICATION, SettingsMigration.APP_EP_NAME, APP_MIGRATION_LEDGER) {
      override fun loadedScopeContexts(): List<SettingsContext> = listOf(AppSettingsContext)
    },
    PROJECT(SettingsContextKind.PROJECT, SettingsMigration.PROJECT_EP_NAME, PROJECT_MIGRATION_LEDGER) {
      override fun loadedScopeContexts(): List<SettingsContext> =
        ProjectManager.getInstanceIfCreated()?.openProjects.orEmpty().filter { !it.isDisposed }.map { ProjectSettingsContext(it) }
    },
    ;

    /** Returns the scope contexts that can have migrations to run now: the application, or each open project. */
    abstract fun loadedScopeContexts(): List<SettingsContext>
  }

  private companion object {
    private val LOG = logger<SettingsMigrationRunner>()
  }
}

/**
 * Runs the migration check of [scopeContext], the application context or the context of an open project. It returns
 * when the check has launched the migrations that no ledger of [scopeContext] lists, and does not wait for their end.
 * In the unit-test mode the engine starts no check by itself, so a unit test of the engine calls this function.
 */
@TestOnly
suspend fun runMigrationCheck(scopeContext: SettingsContext) {
  (SettingsVault.getInstance() as SettingsVaultImpl).migrationRunner.checkByHand(scopeContext)
}

/** Holds the coroutine scope of the project migrations of its project. The scope ends when the project closes. */
@Service(Service.Level.PROJECT)
internal class ProjectMigrationsCoroutineScope(val coroutineScope: CoroutineScope)
