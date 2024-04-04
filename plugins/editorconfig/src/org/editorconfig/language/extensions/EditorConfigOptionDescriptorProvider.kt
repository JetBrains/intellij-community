// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor

interface EditorConfigOptionDescriptorProvider {
  fun getOptionDescriptors(project: Project): List<EditorConfigOptionDescriptor>
  fun requiresFullSupport(): Boolean
  fun initialize(project: Project) {
  }

  companion object {
    val EP_NAME: ExtensionPointName<EditorConfigOptionDescriptorProvider> =
      ExtensionPointName.create("editorconfig.optionDescriptorProvider")
  }
}
