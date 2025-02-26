// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.testFramework.utils.io.createFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.buildScript
import org.jetbrains.plugins.gradle.testFramework.GradleReloadProjectTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.junit.jupiter.params.ParameterizedTest
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

class GradleSyncOutputTest : GradleReloadProjectTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test sync with lazy task configuration`(gradleVersion: GradleVersion) {
    test(gradleVersion) {
      reloadProject()
      buildViewFixture.assertSyncViewTree {
        assertNode("finished")
      }

      projectRoot.resolve("build.gradle")
        .createParentDirectories().createFile()
        .writeText(buildScript(gradleVersion, GradleDsl.GROOVY) {
          withJavaPlugin()
          withPostfix {
            call("tasks.register", string("my-jar-task"), code("Jar")) {
              call("project.configurations.create", "my-jar-configuration")
            }
          }
        })
      reloadProject()
      buildViewFixture.assertSyncViewTree {
        assertNode("finished")
      }

      projectRoot.resolve("build.gradle")
        .writeText(buildScript(gradleVersion, GradleDsl.GROOVY) {
          withJavaPlugin()
          withPostfix {
            call("tasks.register", string("my-task")) {
              call("project.configurations.create", "my-configuration")
            }
            call("tasks.register", string("my-jar-task"), code("Jar")) {
              call("project.configurations.create", "my-jar-configuration")
            }
          }
        })
      reloadProject()
      buildViewFixture.assertSyncViewTree {
        assertNode("finished")
      }
    }
  }
}