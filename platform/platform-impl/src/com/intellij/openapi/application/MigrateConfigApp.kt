// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.application.ApplicationStarter.Companion.ANY_MODALITY
import com.intellij.openapi.diagnostic.thisLogger

/**
 * The purpose of [MigrateConfigApp] is to perform a config migration ("import config") from the old installation of the IDE in
 * an automated way (without UI and user dialogs). In case of the IDE update, this command may be a prerequisite for the
 * [com.intellij.openapi.command.impl.UpdatePluginsApp] command, which does not perform a config import and depends on the IDE config to update
 * the plugins correctly ([IDEA-327560](https://youtrack.jetbrains.com/issue/IDEA-327560) custom plugin repositories are stored in the configuration file).
 * If config migration is expected, but is not allowed by the current policy to be performed without UI, [MigrateConfigApp] exits with code 1.
 * A side effect of this command is that [com.intellij.openapi.application.ConfigImportHelper.FIRST_SESSION_KEY] will be false on the next IDE start.
 */
class MigrateConfigApp : ApplicationStarter {
  override fun premain(args: List<String>) {
    System.setProperty("idea.skip.indices.initialization", "true")
  }

  override val requiredModality: Int = ANY_MODALITY // run on EDT

  /**
   * Implementation notes:
   * - EULA agreement is not checked here before migration as it is in a normal startup flow
   */
  override fun main(args: List<String>) {
    // TODO maybe put this check into premain to exit faster if import is not needed
    val importIsNeeded = ConfigImportHelper.isConfigImportExpected(PathManager.getConfigDir())
    if (!importIsNeeded) {
      thisLogger().info("Config migration is not needed")
      System.exit(0)
    }

    try {
      ConfigImportHelper.importConfigsTo(false, PathManager.getConfigDir(), listOf(), thisLogger(), true)
    } catch (e: UnsupportedOperationException) {
      thisLogger().warn("Config migration requires interaction with user", e)
      System.exit(1)
    } catch (e: Throwable) {
      thisLogger().error("Config migration failed unexpectedly", e)
      System.exit(2)
    }
    thisLogger().info("Config migration has completed successfully")

    System.exit(0)
  }
}