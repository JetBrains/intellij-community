// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.module.ConfigurationErrorDescription
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.project.stateStore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.loadProjectAndCheckResults
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class LoadInvalidProjectTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

    private val testDataRoot: Path
      get() = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("platform/platform-tests/testData/configurationStore/invalid")
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
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(disposable.disposable, errors::add)
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
  fun `unknown classpath provider in iml`() {
    loadProjectAndCheckResults("unknown-classpath-provider-in-iml") { project ->
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

  @Test
  fun `empty library xml`() {
    loadProjectAndCheckResults("empty-library-xml") { project ->
      //this repeats behavior in the old model: if library configuration file cannot be parsed it's silently ignored
      assertThat(LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries).isEmpty()
      assertThat(errors).isEmpty()
    }
  }

  @Test
  fun `duplicating libraries`() {
    loadProjectAndCheckResults("duplicating-libraries") { project ->
      assertThat(LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.single().name).isEqualTo("foo")
      assertThat(errors).isEmpty()

      project.stateStore.save()
      val expected = directoryContentOf(testDataRoot.resolve("duplicating-libraries-fixed"))
          .mergeWith(directoryContentOf(testDataRoot.resolve ("common")))
      val projectDir = project.stateStore.directoryStorePath!!.parent
      projectDir.assertMatches(expected)
    }
  }

  @Test
  fun `duplicating artifacts`() {
    loadProjectAndCheckResults("duplicating-artifacts") { project ->
      assertThat(LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.single().name).isEqualTo("foo")
      assertThat(errors).isEmpty()

      project.stateStore.save()
      val expected = directoryContentOf(testDataRoot.resolve("duplicating-libraries-fixed"))
          .mergeWith(directoryContentOf(testDataRoot.resolve ("common")))
      val projectDir = project.stateStore.directoryStorePath!!.parent
      projectDir.assertMatches(expected)
    }
  }

  private fun loadProjectAndCheckResults(testDataDirName: String, checkProject: suspend (Project) -> Unit) {
    return loadProjectAndCheckResults(listOf(testDataRoot.resolve("common"), testDataRoot.resolve(testDataDirName)), tempDirectory, checkProject)
  }
}
