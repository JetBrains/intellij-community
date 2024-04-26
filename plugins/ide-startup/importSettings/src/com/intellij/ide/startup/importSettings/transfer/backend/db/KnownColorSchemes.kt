// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.db

import com.intellij.ide.startup.importSettings.models.BundledEditorColorScheme

object KnownColorSchemes {
  val Light: BundledEditorColorScheme = BundledEditorColorScheme.fromManager("IntelliJ Light")!! // here should be ok as they are bundled
  val Darcula: BundledEditorColorScheme = BundledEditorColorScheme.fromManager("Darcula")!!
  val HighContrast: BundledEditorColorScheme = BundledEditorColorScheme.fromManager("High contrast")!!
}