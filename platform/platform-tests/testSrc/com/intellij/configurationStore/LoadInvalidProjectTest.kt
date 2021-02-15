// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.module.ConfigurationErrorDescription
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.loadProjectAndCheckResults
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths

class LoadInvalidProjectTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val tempDirectory = TemporaryDirectory()

  @JvmField
  @Rule
  val disposable = DisposableRule()
  private val errors = ArrayList<ConfigurationErrorDescription>()

  @Before
  fun setUp() {
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(errors::add, disposable.disposable)
  }

  @Test
  fun `load empty iml`() {
    loadProjectAndCheckResults("empty-iml-file") { project ->
      assertThat(ModuleManager.getInstance(project).modules).hasSize(1)
      assertThat(WorkspaceModel.getInstance(project).entityStorage.current.entities(ModuleEntity::class.java).single().name).isEqualTo("foo")
      assertThat(errors.single().description).contains("foo.iml")
    }
  }

  @Test
  fun `malformed xml in iml`() {
    loadProjectAndCheckResults("malformed-xml-in-iml") { project ->
      assertThat(ModuleManager.getInstance(project).modules).hasSize(1)
      assertThat(WorkspaceModel.getInstance(project).entityStorage.current.entities(ModuleEntity::class.java).single().name).isEqualTo("foo")
      assertThat(errors.single().description).contains("foo.iml")
    }
  }

  @Test
  fun `no iml file`() {
    loadProjectAndCheckResults("no-iml-file") { project ->
      //this repeats behavior in the old model: if iml files doesn't exist we create a module with empty configuration and report no errors
      assertThat(ModuleManager.getInstance(project).modules.single().name).isEqualTo("foo")
      assertThat(errors).isEmpty()
    }
  }

  private fun loadProjectAndCheckResults(testDataDirName: String, checkProject: suspend (Project) -> Unit) {
    assumeTrue(ProjectModelRule.isWorkspaceModelEnabled)
    val testDataRoot = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("platform/platform-tests/testData/configurationStore/invalid")
    return loadProjectAndCheckResults(listOf(testDataRoot.resolve("common"), testDataRoot.resolve(testDataDirName)), tempDirectory, checkProject)
  }
}
