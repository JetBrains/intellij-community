// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vswin.parsers

import com.intellij.ide.startup.importSettings.db.KnownColorSchemes
import com.intellij.ide.startup.importSettings.db.KnownLafs
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.providers.vswin.utilities.VSHive
import com.intellij.openapi.diagnostic.logger

class VSParser(private val hive: VSHive) {
  val settings: Settings
  private val logger = logger<VSParser>()

  init {
    val regParser = hive.registry
    val settingsFile = regParser?.settingsFile
    requireNotNull(settingsFile)

    settings = VSXmlParser(settingsFile, hive).toSettings().apply {
      if (laf == null) {
        laf = KnownLafs.Darcula
      }

      when (laf) {
        KnownLafs.Darcula -> syntaxScheme = KnownColorSchemes.Darcula
        KnownLafs.Light -> syntaxScheme = KnownColorSchemes.Light
      }

      plugins.putAll(regParser.extensions)

      //if (hive.productVersionTextRepresentation() == "2015") {
      //    plugins.add(KnownPlugins.XAMLStyler)
      //}

      for (path in regParser.recentProjects) {
        if (!addRecentProjectIfNeeded { path }) break
      }

      if (laf == null) {
        logger.info("Got null for laf, trying registry method")
        laf = regParser.theme
      }
    }
  }
}