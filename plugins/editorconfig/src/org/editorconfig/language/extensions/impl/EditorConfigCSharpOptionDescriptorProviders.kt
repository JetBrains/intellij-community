// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.extensions.impl

import org.editorconfig.language.extensions.EditorConfigCSharpOptionDescriptorProviderBase

class EditorConfigReSharperOptionDescriptorProvider : EditorConfigCSharpOptionDescriptorProviderBase() {
  override val filePath = "schemas/editorconfig/resharper.json"
}

class EditorConfigMsFormattingOptionDescriptorProvider : EditorConfigCSharpOptionDescriptorProviderBase() {
  override val filePath = "schemas/editorconfig/msformatting.json"
}

class EditorConfigMsLanguageOptionDescriptorProvider : EditorConfigCSharpOptionDescriptorProviderBase() {
  override val filePath = "schemas/editorconfig/mslanguage.json"
}

class EditorConfigMsNamingOptionDescriptorProvider : EditorConfigCSharpOptionDescriptorProviderBase() {
  override val filePath = "schemas/editorconfig/msnaming.json"
}

class EditorConfigMsMiscOptionDescriptorProvider : EditorConfigCSharpOptionDescriptorProviderBase() {
  override val filePath = "schemas/editorconfig/msmisc.json"
}