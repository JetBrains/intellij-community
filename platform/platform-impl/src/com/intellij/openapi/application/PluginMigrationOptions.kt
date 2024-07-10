// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Path

internal class PluginMigrationOptions(
  val previousVersion: String?,
  val currentProductVersion: String,
  val newConfigDir: Path,
  val oldConfigDir: Path,
  val pluginsToMigrate: MutableList<IdeaPluginDescriptor>,
  val pluginsToDownload: MutableList<IdeaPluginDescriptor>,
  val log: Logger
)