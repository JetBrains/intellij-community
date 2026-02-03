// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.java

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.platform.workspace.jps.bridge.impl.JpsUrlListBridge
import com.intellij.platform.workspace.jps.bridge.impl.module.JpsModuleBridge
import com.intellij.platform.workspace.jps.bridge.impl.reportModificationAttempt
import org.jetbrains.jps.model.JpsUrlList
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.java.JpsJavaModuleExtension
import org.jetbrains.jps.model.java.LanguageLevel

internal class JpsJavaModuleExtensionBridge(private val javaSettingsEntity: JavaModuleSettingsEntity?, parentElement: JpsModuleBridge)
  : JpsElementBase<JpsJavaModuleExtensionBridge>(), JpsJavaModuleExtension {
    
  init {
    parent = parentElement
  }
  
  private val javadocUrlList by lazy(LazyThreadSafetyMode.PUBLICATION) {
    //todo include data from JavaModuleExternalPaths to JavaModuleSettingsEntity and use it here (IJPL-15920) 
    JpsUrlListBridge(emptyList(), this)
  }
  private val annotationUrlList by lazy(LazyThreadSafetyMode.PUBLICATION) {
    //todo include data from JavaModuleExternalPaths to JavaModuleSettingsEntity and use it here (IJPL-15920) 
    JpsUrlListBridge(emptyList(), this)
  }

  override fun getOutputUrl(): String? = javaSettingsEntity?.compilerOutput?.url

  override fun getTestOutputUrl(): String? = javaSettingsEntity?.compilerOutputForTests?.url ?: outputUrl

  override fun getLanguageLevel(): LanguageLevel? = javaSettingsEntity?.languageLevelId?.let { 
    levelId -> LanguageLevel.entries.find { it.name == levelId }
  }

  override fun getJavadocRoots(): JpsUrlList = javadocUrlList

  override fun getAnnotationRoots(): JpsUrlList = annotationUrlList

  override fun isInheritOutput(): Boolean = javaSettingsEntity?.inheritedCompilerOutput ?: true

  override fun isExcludeOutput(): Boolean = javaSettingsEntity?.excludeOutput ?: true

  override fun setOutputUrl(outputUrl: String?) {
    reportModificationAttempt()
  }

  override fun setTestOutputUrl(testOutputUrl: String?) {
    reportModificationAttempt()
  }

  override fun setLanguageLevel(languageLevel: LanguageLevel?) {
    reportModificationAttempt()
  }

  override fun setInheritOutput(inheritOutput: Boolean) {
    reportModificationAttempt()
  }

  override fun setExcludeOutput(excludeOutput: Boolean) {
    reportModificationAttempt()
  }
}
