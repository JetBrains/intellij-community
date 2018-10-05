// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.extensions

import com.google.gson.JsonSyntaxException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VfsUtil
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import org.editorconfig.language.schema.parser.EditorConfigOptionDescriptorJsonDeserializer

abstract class EditorConfigJsonFileOptionDescriptorProviderBase : EditorConfigOptionDescriptorProvider {
  protected abstract val filePath: String

  private val cachedDescriptors by lazy {
    deserialize(loadFileContent())
  }

  final override fun getOptionDescriptors() = cachedDescriptors

  private fun deserialize(text: String) = try {
    EditorConfigOptionDescriptorJsonDeserializer
      .buildGson()
      .fromJson(text, Array<EditorConfigOptionDescriptor?>::class.java)
      .filterNotNull()
  }
  catch (ex: JsonSyntaxException) {
    logger<EditorConfigJsonFileOptionDescriptorProviderBase>()
      .warn("Json syntax error in descriptor")
    emptyList<EditorConfigOptionDescriptor>()
  }

  private fun loadFileContent(): String {
    val url = javaClass.classLoader.getResource(filePath)
    val virtualFile = VfsUtil.findFileByURL(url) ?: return ""
    return VfsUtil.loadText(virtualFile)
  }
}
