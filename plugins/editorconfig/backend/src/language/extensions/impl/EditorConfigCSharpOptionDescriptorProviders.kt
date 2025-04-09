// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.extensions.impl

import com.intellij.openapi.project.Project
import org.editorconfig.EditorConfigRegistry
import org.editorconfig.language.extensions.EditorConfigCSharpOptionDescriptorProviderBase
import org.editorconfig.language.extensions.EditorConfigJsonFileOptionDescriptorProviderBase
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor

/**
 * This provider can provide obsolete descriptors, but they are better than nothing.
 * In Rider, up-to date ones can be obtained from R# backend instead.
 * @see EditorConfigCompleteReSharperOptionDescriptorProvider
 */
class EditorConfigIncompleteReSharperOptionDescriptorProvider : EditorConfigJsonFileOptionDescriptorProviderBase() {
  override val filePath = "schemas/editorconfig/resharper.json"
  override fun getOptionDescriptors(project: Project): List<EditorConfigOptionDescriptor> {
    if (!EditorConfigRegistry.shouldSupportReSharper()) return super.getOptionDescriptors(project)
    else return emptyList()
  }

  override fun requiresFullSupport() = EditorConfigRegistry.shouldSupportDotNet()
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