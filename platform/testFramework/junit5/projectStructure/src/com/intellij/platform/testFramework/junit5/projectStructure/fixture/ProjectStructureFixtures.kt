// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture

import com.intellij.codeInsight.multiverse.MultiverseTestEnabler
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl.MultiverseFixtureInitializer
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly

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
