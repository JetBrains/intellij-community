// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleJvmFixture
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.junit.jupiter.params.ParameterizedTest

class GradleSyncOutputTest : GradleSyncOutputTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test sync with lazy task configuration`(gradleVersion: GradleVersion): Unit = runBlocking {
    gradleJvmFixture(gradleVersion, JavaVersionRestriction.NO, asDisposable())
      .installProjectSettingsConfigurator(asDisposable())

    createSettingsFile(gradleVersion) {
      setProjectName(project.name)
    }
    reloadProject()
    assertSyncViewTree {
      assertNode("finished")
    }

    createBuildFile(gradleVersion) {
      withJavaPlugin()
      withPostfix {
        call("tasks.register<Jar>", string("my-jar-task")) {
          call("project.configurations.create", "my-jar-configuration")
        }
      }
    }
    reloadProject()
    assertSyncViewTree {
      assertNode("finished")
    }

    createBuildFile(gradleVersion) {
      withJavaPlugin()
      withPostfix {
        call("tasks.register", string("my-task")) {
          call("project.configurations.create", "my-configuration")
        }
        call("tasks.register<Jar>", string("my-jar-task")) {
          call("project.configurations.create", "my-jar-configuration")
        }
      }
    }
    reloadProject()
    assertSyncViewTree {
      assertNode("finished")
    }
  }
}