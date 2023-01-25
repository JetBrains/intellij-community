// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.bridgeEntities.JavaModuleSettingsEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jdom.Element
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.*

internal object JavaSettingsSerializer {
  fun saveJavaSettings(javaSettings: JavaModuleSettingsEntity?, rootManagerElement: Element) {
    if (javaSettings == null) {
      if (javaPluginPresent()) {
        rootManagerElement.setAttribute(INHERIT_COMPILER_OUTPUT_ATTRIBUTE, true.toString())
        rootManagerElement.addContent(Element(EXCLUDE_OUTPUT_TAG))
      }

      return
    }

    if (javaSettings.inheritedCompilerOutput) {
      rootManagerElement.setAttribute(INHERIT_COMPILER_OUTPUT_ATTRIBUTE, true.toString())
    }
    else {
      val outputUrl = javaSettings.compilerOutput?.url
      if (outputUrl != null) {
        rootManagerElement.addContent(Element(OUTPUT_TAG).setAttribute(URL_ATTRIBUTE, outputUrl))
      }
      val testOutputUrl = javaSettings.compilerOutputForTests?.url
      if (testOutputUrl != null) {
        rootManagerElement.addContent(Element(TEST_OUTPUT_TAG).setAttribute(URL_ATTRIBUTE, testOutputUrl))
      }
    }
    if (javaSettings.excludeOutput) {
      rootManagerElement.addContent(Element(EXCLUDE_OUTPUT_TAG))
    }
    javaSettings.languageLevelId?.let {
      rootManagerElement.setAttribute(MODULE_LANGUAGE_LEVEL_ATTRIBUTE, it)
    }
  }

  fun loadJavaModuleSettings(rootManagerElement: Element,
                             virtualFileManager: VirtualFileUrlManager,
                             contentRotEntitySource: EntitySource): JavaModuleSettingsEntity? {
    val inheritedCompilerOutput = rootManagerElement.getAttributeAndDetach(INHERIT_COMPILER_OUTPUT_ATTRIBUTE)
    val languageLevel = rootManagerElement.getAttributeAndDetach(MODULE_LANGUAGE_LEVEL_ATTRIBUTE)
    val excludeOutput = rootManagerElement.getChildAndDetach(EXCLUDE_OUTPUT_TAG)
    val compilerOutput = rootManagerElement.getChildAndDetach(OUTPUT_TAG)?.getAttributeValue(URL_ATTRIBUTE)
    val compilerOutputForTests = rootManagerElement.getChildAndDetach(TEST_OUTPUT_TAG)?.getAttributeValue(URL_ATTRIBUTE)

    // According to our logic, java settings entity should produce one of the following attributes.
    //   So, if we don't meet one, we don't create a java settings entity
    return if (inheritedCompilerOutput != null || compilerOutput != null || languageLevel != null || excludeOutput != null || compilerOutputForTests != null) {
      JavaModuleSettingsEntity(inheritedCompilerOutput = inheritedCompilerOutput?.toBoolean() ?: false,
                                                 excludeOutput = excludeOutput != null,
                                                 entitySource = contentRotEntitySource
      ) {
        this.compilerOutput = compilerOutput?.let { virtualFileManager.fromUrl(it) }
        this.compilerOutputForTests = compilerOutputForTests?.let { virtualFileManager.fromUrl(it) }
        this.languageLevelId = languageLevel
      }
    }
    else if (javaPluginPresent()) {
      JavaModuleSettingsEntity(true, true, contentRotEntitySource)
    }
    else null
  }

  private fun javaPluginPresent() = PluginManagerCore.getPlugin(PluginId.findId("com.intellij.java")) != null

  private fun Element.getAttributeAndDetach(name: String): String? {
    val result = getAttributeValue(name)
    removeAttribute(name)
    return result
  }

  private fun Element.getChildAndDetach(cname: String): Element? = getChild(cname)?.also { it.detach() }
}