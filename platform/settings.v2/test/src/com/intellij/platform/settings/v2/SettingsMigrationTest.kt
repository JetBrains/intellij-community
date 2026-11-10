// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionPointDeferredListenersNotification
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.Disposer
import com.intellij.platform.settings.v2.impl.APP_MIGRATION_LEDGER
import com.intellij.platform.settings.v2.impl.PROJECT_MIGRATION_LEDGER
import com.intellij.platform.settings.v2.impl.SettingsMigrationIdentity
import com.intellij.platform.settings.v2.impl.SettingsMigrationLedger
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Checks how the platform runs the settings migrations and records them in the migration ledger. A test registers a
 * migration as a plugin descriptor does: by the class name and an `id`, with no instance. The registration starts
 * the migration check, and a reload starts it again.
 *
 * The application ledger and the ledger of the shared project outlive a test, so each test takes a new migration id.
 *
 * Two checks close together can launch one migration twice. So a test that counts the runs of a migration starts
 * only one check while the migration is not recorded.
 */
@TestApplication
class SettingsMigrationTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    private val migratedKey = SettingsKey("test.migration.migrated", default = "def")

    /** Holds the plugin that declares a test migration unless a test names another one. */
    private val corePlugin: PluginDescriptor get() = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()
  private val projectContext get() = SettingsContext.project(project)
  private val appContext = SettingsContext.app()

  @BeforeEach
  fun resetMigrations() {
    RecordingMigration.reset()
    FailingOnceMigration.reset()
    BlockingMigration.reset()
    CancelledOnceMigration.reset()
  }

  /**
   * Waits for the checks that an earlier reload started, such as the reload of the project at its open. Otherwise, such
   * a check can launch a migration of the test a second time.
   */
  @BeforeEach
  fun awaitEarlierChecks(): Unit = runBlocking {
    SettingsVault.getInstance().awaitReady(projectContext)
    awaitApplicationCheckEnd()
    awaitProjectCheckEnd()
  }

  /**
   * Writes the pending ledger saves. Otherwise, a save outlives the test: it can put the application ledger into the
   * config of a later test, or reach the VFS after the project closes.
   */
  @AfterEach
  fun flushLedgers(): Unit = runBlocking {
    SettingsVault.getInstance().flush(appContext)
    SettingsVault.getInstance().flush(projectContext)
  }

  @Test
  fun `a project migration runs once for the project and gets its context`(): Unit = runBlocking {
    val id = newMigrationId()
    registerProjectMigration<RecordingMigration>(id)

    awaitEquals(true) { migration(id).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed }
    val context = RecordingMigration.contexts.single()
    assertEquals(projectContext.identity, context.identity)
    assertSame(project, (context as ProjectAware).project)
    Instant.parse(PROJECT_MIGRATION_LEDGER[projectContext].completed.getValue(migration(id).ledgerEntryKey))

    reloadAfterSaves(projectContext)
    awaitProjectCheckEnd()
    assertEquals(1, RecordingMigration.contexts.size)
    assertEquals(1, RecordingMigration.instances.get())
  }

  @Test
  fun `an application migration runs once for the application`(): Unit = runBlocking {
    val id = newMigrationId()
    registerMigration<RecordingMigration>(SettingsMigration.APP_EP_NAME, id)

    awaitEquals(true) { migration(id).ledgerEntryKey in APP_MIGRATION_LEDGER[appContext].completed }
    assertEquals(appContext.identity, RecordingMigration.contexts.single().identity)
    assertFalse(migration(id).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed)

    reloadAfterSaves(appContext)
    awaitApplicationCheckEnd()
    assertEquals(1, RecordingMigration.contexts.size)
  }

  @Test
  fun `a migration that the ledger lists gets no instance`(): Unit = runBlocking {
    val id = newMigrationId()
    PROJECT_MIGRATION_LEDGER[projectContext] = PROJECT_MIGRATION_LEDGER[projectContext].withCompleted(migration(id))

    registerProjectMigration<RecordingMigration>(id)
    awaitProjectCheckEnd()

    assertEquals(0, RecordingMigration.instances.get())
  }

  @Test
  fun `a migration that only the team ledger lists does not run`(): Unit = runBlocking {
    val id = newMigrationId()
    EDIT_TEAM_LAYER[projectContext] = true
    try {
      PROJECT_MIGRATION_LEDGER[projectContext] = SettingsMigrationLedger().withCompleted(migration(id))
    }
    finally {
      EDIT_TEAM_LAYER[projectContext] = false
    }
    // The local ledger wins a plain read, and it does not list the migration.
    PROJECT_MIGRATION_LEDGER[projectContext] = SettingsMigrationLedger().withCompleted(migration(newMigrationId()))
    assertFalse(migration(id).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed)
    assertTrue(PROJECT_MIGRATION_LEDGER.values(projectContext).any { migration(id).ledgerEntryKey in it.completed },
               "the team ledger lists the migration")

    registerProjectMigration<RecordingMigration>(id)
    awaitProjectCheckEnd()

    assertEquals(0, RecordingMigration.instances.get())
  }

  @Test
  fun `a migration that throws is logged, is not recorded, and runs again after a reload`(): Unit = runBlocking {
    val id = newMigrationId()
    collectingErrors { loggedErrors ->
      registerProjectMigration<FailingOnceMigration>(id)
      // The run logs its failure as its last step. A new check would run the migration again, so none starts before the assert.
      awaitEquals(true) { loggedErrors.any { FailingOnceMigration.FAILURE in it } }
      assertFalse(migration(id).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed)
    }

    reloadAfterSaves(projectContext)

    awaitEquals(true) { migration(id).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed }
    assertEquals(2, FailingOnceMigration.runs.get())
  }

  @Test
  fun `a cancelled migration is not recorded, logs no error, and runs again after a reload`(): Unit = runBlocking {
    val id = newMigrationId()
    val (_, errors) = collectingErrors {
      registerProjectMigration<CancelledOnceMigration>(id)
      CancelledOnceMigration.cancelled.await()
      assertFalse(migration(id).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed)

      reloadAfterSaves(projectContext)

      awaitEquals(true) { migration(id).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed }
    }
    assertEquals(emptyList<String>(), errors)
    assertEquals(2, CancelledOnceMigration.runs.get())
  }

  @Test
  fun `a migration whose ledger entry is removed runs again after a reload`(): Unit = runBlocking {
    val id = newMigrationId()
    registerProjectMigration<RecordingMigration>(id)
    awaitEquals(true) { migration(id).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed }

    val ledger = PROJECT_MIGRATION_LEDGER[projectContext]
    PROJECT_MIGRATION_LEDGER[projectContext] = ledger.copy(completed = ledger.completed - migration(id).ledgerEntryKey)
    reloadAfterSaves(projectContext)

    awaitEquals(2) { RecordingMigration.contexts.size }
    awaitEquals(true) { migration(id).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed }
  }

  @Test
  fun `two plugins with the same migration id both run and both get a ledger entry`(): Unit = runBlocking {
    val id = newMigrationId()
    val otherPlugin = PluginManagerCore.loadedPlugins.first {
      // The core plugin loads the test classes, so a plugin with the same class loader loads them the same way.
      it.pluginId != corePlugin.pluginId && it.pluginClassLoader === corePlugin.pluginClassLoader
    }

    registerProjectMigration<RecordingMigration>(id)
    awaitEquals(true) { migration(id).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed }

    // The entry of the core plugin is in the ledger now, and it must not count for the other plugin.
    registerProjectMigration<RecordingMigration>(id, plugin = otherPlugin)
    awaitEquals(true) { migration(id, otherPlugin).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed }
    assertEquals(2, RecordingMigration.contexts.size)
  }

  @Test
  fun `a migration with no id never runs and is logged`(): Unit = runBlocking {
    val (_, errors) = collectingErrors {
      val registration = Disposer.newDisposable(disposable, "migration with no id")
      try {
        registerProjectMigration<RecordingMigration>(id = null, registration)
        awaitProjectCheckEnd()
      }
      finally {
        Disposer.dispose(registration)
      }
    }

    assertTrue(errors.any { "has no id" in it }, "errors: $errors")
    assertEquals(0, RecordingMigration.instances.get())
  }

  @Test
  fun `a running migration holds neither another migration nor a write`(): Unit = runBlocking {
    val blockedId = newMigrationId()
    val otherId = newMigrationId()
    try {
      // One check launches both, so no second check launches the blocked migration again.
      registerMigrations(SettingsMigration.PROJECT_EP_NAME, listOf(BlockingMigration::class.java to blockedId,
                                                                  RecordingMigration::class.java to otherId))
      BlockingMigration.started.await()

      awaitEquals(true) { migration(otherId).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed }
      migratedKey[projectContext] = "written"
      assertEquals("written", migratedKey[projectContext])
      assertFalse(migration(blockedId).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed)
    }
    finally {
      BlockingMigration.release.complete(Unit)
    }
    awaitEquals(true) { migration(blockedId).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed }
  }

  @Test
  fun `a migration that throws does not stop a migration that runs in parallel with it`(): Unit = runBlocking {
    val blockedId = newMigrationId()
    val failingId = newMigrationId()
    collectingErrors { loggedErrors ->
      try {
        // One registration call starts one check, which launches both migrations.
        registerMigrations(SettingsMigration.PROJECT_EP_NAME, listOf(BlockingMigration::class.java to blockedId,
                                                                    FailingWhileBlockedMigration::class.java to failingId))
        awaitEquals(true) { loggedErrors.any { FailingWhileBlockedMigration.FAILURE in it } }
        assertFalse(migration(blockedId).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed)
      }
      finally {
        BlockingMigration.release.complete(Unit)
      }
    }

    awaitEquals(true) { migration(blockedId).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed }
    assertFalse(migration(failingId).ledgerEntryKey in PROJECT_MIGRATION_LEDGER[projectContext].completed)
  }

  /**
   * Writes the pending saves under [scopeContext], then reloads it. A reload that reads a config before its pending
   * save lands installs the old content, so a ledger entry of a migration can get lost. The flush keeps that vault
   * race out of the migration checks.
   */
  private suspend fun reloadAfterSaves(scopeContext: SettingsContext) {
    SettingsVault.getInstance().flush(scopeContext)
    SettingsVault.getInstance().reload(scopeContext)
  }

  private fun newMigrationId(): String = "test.migration.${UUID.randomUUID()}"

  /** Returns the identity of the migration [id] of [plugin]. Every ledger entry key of this test comes from it. */
  private fun migration(id: String, plugin: PluginDescriptor = corePlugin): SettingsMigrationIdentity =
    SettingsMigrationIdentity(plugin.pluginId, id)

  private inline fun <reified M : SettingsMigration> registerProjectMigration(
    id: String?,
    parentDisposable: Disposable = disposable,
    plugin: PluginDescriptor = corePlugin,
  ) {
    registerMigration<M>(SettingsMigration.PROJECT_EP_NAME, id, parentDisposable, plugin)
  }

  /**
   * Registers [M] as an extension of [extensionPoint] with [id] in [plugin]. See [registerMigrations].
   */
  private inline fun <reified M : SettingsMigration> registerMigration(
    extensionPoint: ExtensionPointName<SettingsMigration>,
    id: String?,
    parentDisposable: Disposable = disposable,
    plugin: PluginDescriptor = corePlugin,
  ) {
    registerMigrations(extensionPoint, listOf(M::class.java to id), parentDisposable, plugin)
  }

  /**
   * Registers [migrations] as extensions of [extensionPoint] in [plugin], as a plugin descriptor does: by the class
   * name, with no instance. One call notifies the listeners once, so one migration check covers all [migrations]. The
   * registration ends with [parentDisposable].
   *
   * @param migrations the implementation class and the `id` of each extension
   */
  private fun registerMigrations(
    extensionPoint: ExtensionPointName<SettingsMigration>,
    migrations: List<Pair<Class<out SettingsMigration>, String?>>,
    parentDisposable: Disposable = disposable,
    plugin: PluginDescriptor = corePlugin,
  ) {
    val point = extensionPoint.point as ExtensionPointImpl<SettingsMigration>
    val registered = migrations.map { (implementation, id) -> implementation.name to id }
    val descriptors = registered.map { (implementation, id) ->
      ExtensionDescriptor(implementation, os = null, orderId = id, order = LoadingOrder.ANY, element = null, hasExtraAttributes = false)
    }
    val notifications = ArrayList<ExtensionPointDeferredListenersNotification>()
    point.registerExtensions(descriptors, plugin, notifications)
    notifications.forEach { it.notify.run() }
    Disposer.register(parentDisposable) {
      point.unregisterExtensions({ className, adapter ->
        (className to adapter.orderId) !in registered || adapter.pluginDescriptor !== plugin
      }, false)
    }
  }

  /** Waits for the end of a project migration check. See [awaitCheckEnd]. */
  private suspend fun awaitProjectCheckEnd() {
    awaitCheckEnd(SettingsMigration.PROJECT_EP_NAME, PROJECT_MIGRATION_LEDGER, projectContext)
  }

  /** Waits for the end of an application migration check. See [awaitCheckEnd]. */
  private suspend fun awaitApplicationCheckEnd() {
    awaitCheckEnd(SettingsMigration.APP_EP_NAME, APP_MIGRATION_LEDGER, appContext)
  }

  /**
   * Waits until a migration check of [scopeContext] that sees a new marker migration has launched its migrations. It
   * registers the marker in [extensionPoint] and waits for it in [ledger]. That check also covers every migration
   * that is registered now. A skipped migration is never launched, but a launched one runs in parallel with the
   * marker. So the wait proves a skip, not the end of another run.
   */
  private suspend fun awaitCheckEnd(
    extensionPoint: ExtensionPointName<SettingsMigration>,
    ledger: SettingsKey<SettingsMigrationLedger>,
    scopeContext: SettingsContext,
  ) {
    val id = newMigrationId()
    registerMigration<CheckEndMarkerMigration>(extensionPoint, id)
    awaitEquals(true) { migration(id).ledgerEntryKey in ledger[scopeContext].completed }
  }
}

/** Records each instance and the context of each run. */
internal class RecordingMigration : SettingsMigration {
  init {
    instances.incrementAndGet()
  }

  override suspend fun migrate(context: SettingsContext) {
    contexts.add(context)
  }

  companion object {
    val instances = AtomicInteger()
    val contexts = CopyOnWriteArrayList<SettingsContext>()

    fun reset() {
      instances.set(0)
      contexts.clear()
    }
  }
}

/** Throws on its first run and returns on each later one. */
internal class FailingOnceMigration : SettingsMigration {
  override suspend fun migrate(context: SettingsContext) {
    if (runs.incrementAndGet() == 1) throw IllegalStateException(FAILURE)
  }

  companion object {
    /** Holds the message of the failure of the first run. */
    const val FAILURE: String = "the first run of the test migration fails"

    val runs = AtomicInteger()

    fun reset() {
      runs.set(0)
    }
  }
}

/** Suspends until the test completes [release]. */
internal class BlockingMigration : SettingsMigration {
  override suspend fun migrate(context: SettingsContext) {
    started.complete(Unit)
    release.await()
  }

  companion object {
    @Volatile
    var started = CompletableDeferred<Unit>()

    @Volatile
    var release = CompletableDeferred<Unit>()

    fun reset() {
      started = CompletableDeferred()
      release = CompletableDeferred()
    }
  }
}

/**
 * Throws once [BlockingMigration] is inside its run. So the failure comes while a parallel migration still runs.
 */
internal class FailingWhileBlockedMigration : SettingsMigration {
  override suspend fun migrate(context: SettingsContext) {
    BlockingMigration.started.await()
    throw IllegalStateException(FAILURE)
  }

  companion object {
    /** Holds the message of the failure. */
    const val FAILURE: String = "the test migration fails while another migration runs"
  }
}

/**
 * Cancels the coroutine of its first run and returns on each later one. The cancellation comes from the inside, as
 * when the project closes. The first run completes [cancelled] as its last step.
 */
internal class CancelledOnceMigration : SettingsMigration {
  override suspend fun migrate(context: SettingsContext) {
    if (runs.incrementAndGet() != 1) return
    try {
      currentCoroutineContext().cancel()
      awaitCancellation()
    }
    finally {
      cancelled.complete(Unit)
    }
  }

  companion object {
    val runs = AtomicInteger()

    @Volatile
    var cancelled = CompletableDeferred<Unit>()

    fun reset() {
      runs.set(0)
      cancelled = CompletableDeferred()
    }
  }
}

/**
 * Does nothing. `SettingsMigrationTest.awaitCheckEnd` waits for its ledger entry to see the end of a migration check.
 * Two checks can run it twice, so nothing counts its runs.
 */
internal class CheckEndMarkerMigration : SettingsMigration {
  override suspend fun migrate(context: SettingsContext) {
  }
}
