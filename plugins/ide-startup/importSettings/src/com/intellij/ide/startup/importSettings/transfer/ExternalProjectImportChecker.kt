// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.openapi.extensions.ExtensionPointName
import java.nio.file.Path

interface ExternalProjectImportChecker {

  companion object {
    val EP_NAME = ExtensionPointName<ExternalProjectImportChecker>("com.intellij.transferSettings.externalProjectImportChecker")
  }

  /**
   * Tells the external project importer whether a particular path should or should not be imported.
   *
   * The decision should be quick enough: during the IDE startup phase, we have little time to decide what to import.
   *
   * @return `true` if the project should be imported, `false` if it should not be imported (veto the import), `null` if the decision should
   * be delegated elsewhere. If no checkers veto the import, the importer subsystem will decide itself.
   */
  fun shouldImportProject(path: Path): Boolean?
}
