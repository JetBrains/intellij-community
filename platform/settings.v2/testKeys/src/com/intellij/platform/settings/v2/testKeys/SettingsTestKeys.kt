// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.testKeys

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsKey
import com.intellij.platform.settings.v2.impl.SettingsKeyPromoter
import org.jetbrains.annotations.ApiStatus

/**
 * Holds the settings keys that an integration test of the settings v2 writes and reads inside the IDE under test.
 * The keys belong to the plugin that loads this module. That is the lambda tests plugin, or the test keys plugin
 * `com.intellij.platform.settings.v2.testKeys.plugin` that a JetBrains Light test adds to the product. A test
 * reaches them over the lambda or the driver, so they live in the IDE and not in the test module.
 */
@ApiStatus.Internal
object SettingsTestKeys {
  /** A text value with no context filter, so a test writes it at the application, a project and a file context. */
  val TEXT: SettingsKey<String> = SettingsKey("testKeys.text", default = "")

  /**
   * Returns the file context of [virtualFile] in [project] from a thread with no read lock, such as a driver call.
   * [SettingsContext.file] needs the read lock, and a driver cannot take it.
   */
  fun fileContext(virtualFile: VirtualFile, project: Project): SettingsContext =
    runReadAction { SettingsContext.file(virtualFile, project) }
}

/** Promotes [SettingsTestKeys], so a write of them is not a missing promotion. */
internal class SettingsTestKeysPromoter : SettingsKeyPromoter {
  override fun promote(register: (SettingsKey<*>) -> Unit) {
    register(SettingsTestKeys.TEXT)
  }
}
