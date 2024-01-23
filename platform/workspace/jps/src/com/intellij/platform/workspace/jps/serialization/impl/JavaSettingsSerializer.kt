// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.platform.workspace.jps.serialization.SerializationContext
import com.intellij.platform.workspace.storage.EntitySource
import org.jdom.Element
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.*

internal object JavaSettingsSerializer {
  fun saveJavaSettings(javaSettings: JavaModuleSettingsEntity?, rootManagerElement: Element, context: SerializationContext) {
    if (javaSettings == null) {
      if (context.isJavaPluginPresent) {
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
                             context: SerializationContext,
                             contentRotEntitySource: EntitySource ): JavaModuleSettingsEntity? {
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
        this.compilerOutput = compilerOutput?.let { context.virtualFileUrlManager.getOrCreateFromUri(it) }
        this.compilerOutputForTests = compilerOutputForTests?.let { context.virtualFileUrlManager.getOrCreateFromUri(it) }
        this.languageLevelId = languageLevel
      }
    }
    else if (context.isJavaPluginPresent) {
      JavaModuleSettingsEntity(true, true, contentRotEntitySource)
    }
    else null
  }

  private fun Element.getAttributeAndDetach(name: String): String? {
    val result = getAttributeValue(name)
    removeAttribute(name)
    return result
  }

  private fun Element.getChildAndDetach(cname: String): Element? = getChild(cname)?.also { it.detach() }
}