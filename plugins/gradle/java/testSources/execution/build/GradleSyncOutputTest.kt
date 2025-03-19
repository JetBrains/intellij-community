// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.util.DEFAULT_SYNC_TIMEOUT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestDisposable
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleJvmTestFixture
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.junit.jupiter.params.ParameterizedTest

class GradleSyncOutputTest : GradleSyncOutputTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test sync with lazy task configuration`(gradleVersion: GradleVersion, @TestDisposable disposable: Disposable) {
    GradleJvmTestFixture.installProjectSettingsConfigurator(gradleVersion, JavaVersionRestriction.NO, disposable)

    timeoutRunBlocking(DEFAULT_SYNC_TIMEOUT) {
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
}