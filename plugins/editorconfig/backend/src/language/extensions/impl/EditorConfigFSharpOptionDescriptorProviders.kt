// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.extensions.impl

import org.editorconfig.language.extensions.EditorConfigFSharpOptionDescriptorProviderBase

class EditorConfigFantomasFormattingOptionDescriptorProvider : EditorConfigFSharpOptionDescriptorProviderBase() {
  override val filePath = "schemas/editorconfig/fantomas.json"
}
