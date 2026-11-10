// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/settings-migrations.spec.md

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Moves settings from an older shape to a newer one. For example, it imports the values of a
 * `PersistentStateComponent`, changes the value type of a key, or splits or merges keys. The platform does not know
 * what a migration reads or writes (ADR 0169).
 *
 * A plugin registers a migration as a `com.intellij.settings.v2.appMigration` or a
 * `com.intellij.settings.v2.projectMigration` extension, with an `id` attribute that is unique within the plugin:
 *
 * ```xml
 * <settings.v2.projectMigration id="myPlugin.importMySettings" implementation="com.example.ImportMySettings"/>
 * ```
 *
 * The platform runs a migration for the application or for a project after the settings of that scope load, when no
 * migration ledger of the scope lists it. A changed config, for example after a branch switch, can make it run again,
 * so a repeated run must give a correct result. The platform creates the instance only to run it (ADR 0168,
 * ADR 0170). The migrations run in parallel and in no order, so two steps that depend on each other are one
 * migration. A write does not wait for a migration (ADR 0171).
 *
 * A migration must be fast. A reload does not cancel a migration that runs. So when the config changes during a run,
 * for example at a branch switch, the migration still writes the values that it read from the old config. The
 * platform then records it as run for the new config, and it does not run there again.
 */
@Experimental
fun interface SettingsMigration {
  /**
   * Runs the migration for [context]. A normal return records the migration as run, so it never runs again for this
   * scope. A migration that throws is logged, and it runs again after the next reload.
   *
   * @param context the scope context of the run: the application context, or the context of the project. A project
   *   context is a [ProjectAware], which gives the project.
   */
  suspend fun migrate(context: SettingsContext)

  @Internal
  companion object {
    /** Holds the migrations that run once per application. Declared in `intellij.platform.settings.v2.xml`. */
    val APP_EP_NAME: ExtensionPointName<SettingsMigration> = ExtensionPointName("com.intellij.settings.v2.appMigration")

    /** Holds the migrations that run once per project. Declared in `intellij.platform.settings.v2.xml`. */
    val PROJECT_EP_NAME: ExtensionPointName<SettingsMigration> = ExtensionPointName("com.intellij.settings.v2.projectMigration")
  }
}
