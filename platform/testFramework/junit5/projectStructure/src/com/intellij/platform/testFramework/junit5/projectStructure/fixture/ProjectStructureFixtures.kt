// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture

import com.intellij.codeInsight.multiverse.MultiverseTestEnabler
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl.MultiverseFixtureInitializer
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.TestFixtureInitializer
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * This fixture allows setting up the project structure via a simple DSL
 */
@TestOnly
fun multiverseProjectFixture(
  withSharedSourceEnabled: Boolean = true,
  openProjectTask: OpenProjectTask = OpenProjectTask.build(),
  openAfterCreation: Boolean = false,
  init: ProjectBuilder.() -> Unit,
): TestFixture<Project> {
  val fixture = testFixture("multiverse-project-fixture") {
    val project = with(MultiverseFixtureInitializer(init)) {
      initializeProjectModel(openProjectTask, openAfterCreation)
    }

    initialized(project) { /* Nothing to dispose of */ }
  }
  return if (withSharedSourceEnabled) fixture.withSharedSourceEnabled() else fixture
}

/**
 * Enables Shared Source support in the given project.
 */
@TestOnly
fun TestFixture<Project>.withSharedSourceEnabled(): TestFixture<Project> = testFixture("shared-source-enabling-fixture") {
  MultiverseTestEnabler.enableSharedSourcesForTheNextProject()
  initialized(this@withSharedSourceEnabled.init()) {}
}

/**
 * Create SDK [sdkName] of [type] with [pathFixture] [Sdk.getHomePath] and register it in [ProjectJdkTable]
 */
@TestOnly
fun TestFixture<Project>.sdkFixture(sdkName: String, type: SdkTypeId, pathFixture: TestFixture<Path>): TestFixture<Sdk> =
  sdkFixtureImpl { jdkTable ->
    val homePath = pathFixture.init()
    val sdk = jdkTable.createSdk(sdkName, type)
    val root = withContext(Dispatchers.IO) { VfsUtil.findFile(homePath, true)!! }
    edtWriteAction {
      val sdkModificator = sdk.sdkModificator
      sdkModificator.homePath = homePath.pathString
      sdkModificator.addRoot(root, OrderRootType.CLASSES)
      sdkModificator.commitChanges()
    }
    return@sdkFixtureImpl sdk
  }

/**
 * Create SDK using [sdkProvider] and register it in [ProjectJdkTable].
 */
@TestOnly
fun TestFixture<Project>.sdkFixture(sdkProvider: suspend TestFixtureInitializer.R<Sdk>.() -> Sdk): TestFixture<Sdk> =
  sdkFixtureImpl { sdkProvider() }

@TestOnly
private fun TestFixture<Project>.sdkFixtureImpl(sdkProvider: suspend TestFixtureInitializer.R<Sdk>.(ProjectJdkTable) -> Sdk): TestFixture<Sdk> =
  testFixture {
    val project = this@sdkFixtureImpl.init()
    val projectJDKTable = ProjectJdkTable.getInstance(project)
    val sdk = sdkProvider(projectJDKTable)
    writeAction {
      projectJDKTable.addJdk(sdk)
    }
    initialized(sdk) {
      writeAction {
        ProjectJdkTable.getInstance(project).removeJdk(sdk)
      }
    }
  }
