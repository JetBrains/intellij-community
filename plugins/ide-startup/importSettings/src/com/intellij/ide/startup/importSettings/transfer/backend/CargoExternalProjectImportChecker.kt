// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend

import com.intellij.ide.startup.importSettings.transfer.ExternalProjectImportChecker
import com.intellij.openapi.util.registry.Registry
import java.nio.file.Path
import kotlin.io.path.exists

class CargoExternalProjectImportChecker : ExternalProjectImportChecker {

  override fun shouldImportProject(path: Path): Boolean? {
    if (Registry.`is`("transferSettings.vscode.onlyCargoToml")) {
      return path.resolve("Cargo.toml").exists()
    }

    return null
  }
}
