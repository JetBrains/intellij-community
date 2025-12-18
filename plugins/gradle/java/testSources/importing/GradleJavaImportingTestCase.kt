// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.java.library.LibraryWithMavenCoordinatesProperties
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil

abstract class GradleJavaImportingTestCase : GradleImportingTestCase() {

  private fun getLanguageLevelForProjectSdk(project: Project): LanguageLevel? {
    val projectRootManager = ProjectRootManager.getInstance(project)
    val projectSdk = projectRootManager.projectSdk
    val projectSdkVersion = JavaSdkVersionUtil.getJavaSdkVersion(projectSdk) ?: return null
    return projectSdkVersion.maxLanguageLevel
  }

  private fun getLanguageLevelForProject(project: Project): LanguageLevel? {
    val languageLevelProjectExtension = LanguageLevelProjectExtension.getInstance(project)
    if (languageLevelProjectExtension.isDefault) {
      return getLanguageLevelForProjectSdk(project)
    }
    else {
      return languageLevelProjectExtension.languageLevel
    }
  }

  fun getLanguageLevelForModule(moduleName: String): LanguageLevel? {
    val module = getModule(moduleName)
    val moduleLanguageLevel = LanguageLevelUtil.getCustomLanguageLevel(module)
    return moduleLanguageLevel ?: getLanguageLevelForProject(module.project)
  }

  private fun getBytecodeTargetLevelForProject(): String? {
    val compilerConfiguration = CompilerConfiguration.getInstance(myProject)
    return compilerConfiguration.projectBytecodeTarget
  }

  fun getBytecodeTargetLevelForModule(moduleName: String): String? {
    val module = getModule(moduleName)
    val compilerConfiguration = CompilerConfiguration.getInstance(myProject)
    return compilerConfiguration.getBytecodeTargetLevel(module)
  }

  fun getSdkForModule(moduleName: String): Sdk? {
    return ModuleRootManager.getInstance(getModule(moduleName)).sdk
  }

  fun setProjectLanguageLevel(languageLevel: LanguageLevel) {
    IdeaTestUtil.setProjectLanguageLevel(myProject, languageLevel)
  }

  fun setProjectTargetBytecodeVersion(targetBytecodeVersion: String) {
    val compilerConfiguration = CompilerConfiguration.getInstance(myProject)
    compilerConfiguration.projectBytecodeTarget = targetBytecodeVersion
  }

  fun assertProjectLanguageLevel(languageLevel: LanguageLevel?) {
    assertEquals(languageLevel, getLanguageLevelForProject(myProject))
  }

  fun assertModuleLanguageLevel(moduleName: String, languageLevel: LanguageLevel?) {
    assertEquals(languageLevel, getLanguageLevelForModule(moduleName))
  }

  fun assertProjectTargetBytecodeVersion(version: String) {
    val targetBytecodeVersion = getBytecodeTargetLevelForProject()
    assertEquals(version, targetBytecodeVersion)
  }

  fun assertModuleTargetBytecodeVersion(moduleName: String, version: String) {
    val targetBytecodeVersion = getBytecodeTargetLevelForModule(moduleName)
    assertEquals(version, targetBytecodeVersion)
  }

  fun assertProjectCompilerArgumentsVersion(vararg expectedCompilerArguments: String) {
    val actualCompilerArguments = CompilerConfiguration.getInstance(myProject).getAdditionalOptions()
    CollectionAssertions.assertEqualsOrdered(expectedCompilerArguments.asList(), actualCompilerArguments)
  }

  fun assertModuleCompilerArgumentsVersion(moduleName: String, vararg expectedCompilerArguments: String) {
    val module = getModule(moduleName)
    val actualCompilerArguments = CompilerConfiguration.getInstance(myProject).getAdditionalOptions(module)
    CollectionAssertions.assertEqualsOrdered(expectedCompilerArguments.asList(), actualCompilerArguments)
  }

  open fun assertProjectLibraryCoordinates(libraryName: String,
                                           groupId: String?,
                                           artifactId: String?,
                                           version: String?) {
    val lib = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraryByName(libraryName)
    assertNotNull("Library [$libraryName] not found", lib)
    val libraryProperties = (lib as? LibraryEx?)?.properties
    assertInstanceOf(libraryProperties, LibraryWithMavenCoordinatesProperties::class.java)
    val coords = (libraryProperties as LibraryWithMavenCoordinatesProperties).mavenCoordinates
    assertNotNull("Expected non-empty maven coordinates", coords)
    coords!!
    assertEquals("Unexpected groupId", groupId, coords.groupId)
    assertEquals("Unexpected artifactId", artifactId, coords.artifactId)
    assertEquals("Unexpected version", version, coords.version)
  }

  fun assertProjectSdk(expectedLevel: JavaSdkVersion?) {
    val sdk = ProjectRootManager.getInstance(myProject).projectSdk
    assertEquals(expectedLevel, JavaSdkVersionUtil.getJavaSdkVersion(sdk))
  }

  fun assertModuleSdk(moduleName: String, expectedLevel: JavaSdkVersion?) {
    val module = getModule(moduleName)
    val sdk = ModuleRootManager.getInstance(module).sdk
    assertEquals(expectedLevel, JavaSdkVersionUtil.getJavaSdkVersion(sdk))
  }
}