// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.parsers.data

import com.intellij.ide.startup.importSettings.providers.vswin.parsers.data.VSParsedData

internal class TextEditorGeneralParsedData(val bracePairColorization: Boolean) : VSParsedData {
  companion object {
    const val KEY: String = "Text Editor_General"
  }
}
