// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.options.Configurable
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.PathUtil
import java.nio.file.Paths

internal abstract class KotlinObjectExtensionRegistrationInspectionTestBase : JavaCodeInsightFixtureTestCase() {

  protected abstract val testedInspection: LocalInspectionTool

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(testedInspection)
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibraryWithXmlResources(FileType::class.java,
                                             "platform-core-api", "platform-core-resources", "intellij.platform.resources")
    moduleBuilder.addLibraryWithXmlResources(IntentionAction::class.java,
                                             "platform-analysis-api", "platform-analysis-resources", "intellij.platform.analysis")
    moduleBuilder.addLibrary("platform-api", PathUtil.getJarPathForClass(Configurable::class.java))
    moduleBuilder.addLibrary("platform-ide-impl", PathUtil.getJarPathForClass(FileTypeManagerImpl::class.java))
  }

  private fun JavaModuleFixtureBuilder<*>.addLibraryWithXmlResources(clazzFromModule: Class<*>,
                                                                     libraryName: String,
                                                                     resourcesLibraryName: String,
                                                                     resourcesModuleName: String) {
    val jarPathForIntentionActionClass = PathUtil.getJarPathForClass(clazzFromModule)
    this.addLibrary(libraryName, jarPathForIntentionActionClass)
    this.addLibrary(resourcesLibraryName, Paths.get(jarPathForIntentionActionClass).resolveSibling(resourcesModuleName).toString())
  }

}
