// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.extensions

import org.editorconfig.EditorConfigRegistry

abstract class EditorConfigFSharpOptionDescriptorProviderBase : EditorConfigJsonFileOptionDescriptorProviderBase() {
  final override fun requiresFullSupport() = EditorConfigRegistry.shouldSupportDotNet()
}
