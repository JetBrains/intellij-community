// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.services.impl

import com.intellij.openapi.project.Project
import org.editorconfig.language.extensions.EditorConfigOptionDescriptorProvider
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import org.jetbrains.annotations.TestOnly

class EditorConfigOptionLazyDescriptorLoader(project: Project) {
  var fullySupportedDescriptors: EditorConfigOptionDescriptorStorage = EditorConfigOptionDescriptorStorage(emptyList())
    private set
  var partiallySupportedDescriptors: EditorConfigOptionDescriptorStorage = EditorConfigOptionDescriptorStorage(emptyList())
    private set

  init {
    loadDescriptors(project)
  }

  private fun loadDescriptors(project: Project) {
    val fullySupportedDescriptors = mutableListOf<EditorConfigOptionDescriptor>()
    val partiallySupportedDescriptors = mutableListOf<EditorConfigOptionDescriptor>()

    fun loadDescriptors(provider: EditorConfigOptionDescriptorProvider) {
      val requiresFullSupport = provider.requiresFullSupport()
      val loadedDescriptors = provider.getOptionDescriptors(project)
      val destination =
        if (requiresFullSupport) fullySupportedDescriptors
        else partiallySupportedDescriptors

      destination.addAll(loadedDescriptors)
    }

    EditorConfigOptionDescriptorProvider.EP_NAME.extensionList.forEach(::loadDescriptors)

    this.fullySupportedDescriptors = EditorConfigOptionDescriptorStorage(fullySupportedDescriptors)
    this.partiallySupportedDescriptors = EditorConfigOptionDescriptorStorage(partiallySupportedDescriptors)
  }

  @TestOnly
  fun reloadDescriptors(project: Project): Unit = loadDescriptors(project)
}