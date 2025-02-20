// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.openapi.application.edtWriteAction
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleReloadProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.junit.jupiter.params.ParameterizedTest

class GradleSyncOutputTest : GradleReloadProjectTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test sync with lazy task configuration`(gradleVersion: GradleVersion) {
    test(gradleVersion) {
      reloadProject()
      executionFixture.assertSyncViewTree {
        assertNode("finished")
      }
      edtWriteAction {
        projectRoot.createBuildFile(gradleVersion) {
          withJavaPlugin()
          withPostfix {
            call("tasks.register", string("my-jar-task"), code("Jar")) {
              call("project.configurations.create", "my-jar-configuration")
            }
          }
        }
      }
      reloadProject()
      executionFixture.assertSyncViewTree {
        assertNode("finished")
      }
      edtWriteAction {
        projectRoot.createBuildFile(gradleVersion) {
          withJavaPlugin()
          withPostfix {
            call("tasks.register", string("my-task")) {
              call("project.configurations.create", "my-configuration")
            }
            call("tasks.register", string("my-jar-task"), code("Jar")) {
              call("project.configurations.create", "my-jar-configuration")
            }
          }
        }
      }
      reloadProject()
      executionFixture.assertSyncViewTree {
        assertNode("finished")
      }
    }
  }
}