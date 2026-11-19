// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

// a getter, because the startup can load this class before the log is set up
private val LOG: Logger
  get() = logger<MigrationKillSwitch>()

/**
 * The system property `ide.update.migration.disabled` turns off the migration to another product for one install.
 * Support or an administrator sets it to `true`. A missing value or another value changes nothing.
 *
 * The forms of the property:
 * - in the `.vmoptions` file: `-Dide.update.migration.disabled=true`;
 * - in the `idea.properties` file: `ide.update.migration.disabled=true`;
 * - as the first program argument: `-Dide.update.migration.disabled=true`. This form applies only to that process.
 *
 * With the property, a build with a `<distribution>` is a usual update, and the IDE installs no distribution.
 * The IDE shows no "Restart to Update" for an installed distribution.
 * The startup does not redirect to the installed distribution.
 * The IDE does not change `migration.json` and `migration-redirect.json`. Without the property, the state works again.
 */
@ApiStatus.Internal
object MigrationKillSwitch {
  private const val PROPERTY = "ide.update.migration.disabled"

  private val isLogged = AtomicBoolean()

  /**
   * The first `true` result writes one log line.
   * Call it after the log is set up, else the line is lost.
   */
  fun isActive(property: (String) -> String? = System::getProperty): Boolean {
    val isActive = property(PROPERTY).toBoolean()
    if (isActive && isLogged.compareAndSet(false, true)) {
      LOG.info("the migration to another product is off: -D${PROPERTY}=true")
    }
    return isActive
  }
}
