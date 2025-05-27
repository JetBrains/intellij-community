/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.execution.helper

import org.assertj.core.api.Assertions
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.junit.jupiter.params.ParameterizedTest
import com.intellij.util.net.NetUtils
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsAtLeast

class GradleExecutionHelperJvmArgumentsTest : GradleExecutionHelperJvmArgumentsTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test Gradle JVM options resolution with custom properties`(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {

      val customVmOptions = listOf(
        "-Dname=value",
        "-Xmx420m"
      )

      val daemonOptions = executeTaskAndCollectDaemonOptions(customVmOptions)

      Assertions.assertThat(daemonOptions)
        .contains("-Dname=value")
        .contains("-Xmx420m")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test Gradle JVM options resolution with gradle and custom properties`(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {

      writeText("gradle.properties", """
        |org.gradle.jvmargs=\
        |  -Dname=value1 \
        |  -Dname1=value \
        |  -Xmx420m
      """.trimMargin())

      val customVmOptions = listOf(
        "-Dname=value2",
        "-Dname2=value",
        "-Xmx421m"
      )

      val daemonOptions = executeTaskAndCollectDaemonOptions(customVmOptions)

      Assertions.assertThat(daemonOptions)
        .doesNotContain("-Dname=value1")
        .contains("-Dname=value2")
        .contains("-Dname1=value")
        .contains("-Dname2=value")
        .doesNotContain("-Xmx420")
        .contains("-Xmx421m")
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test Gradle JVM options resolution with debug agent in gradle properties and custom properties`(gradleVersion: GradleVersion) {
    assumeThatGradleIsAtLeast(gradleVersion, "7.4") {
      "Gradle debugger port cannot be specified in gradle.properties"
    }
    assumeThatGradleIsAtLeast(gradleVersion, "8.13") {
      "Gradle TAPI's VM options merger cannot recognise org.gradle.debug.* sub-properties in gradle.properties"
    }

    testEmptyProject(gradleVersion) {

      val port = NetUtils.findAvailableSocketPort()

      writeText("gradle.properties", """
        |org.gradle.debug=true
        |org.gradle.debug.port=$port
        |org.gradle.debug.suspend=false
      """.trimMargin())

      val customVmOptions = listOf(
        "-Dname=value",
      )

      val daemonOptions = executeTaskAndCollectDaemonOptions(customVmOptions)

      Assertions.assertThat(daemonOptions)
        .contains("-Dname=value")
        .containsAnyOf(
          "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$port",
          "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$port"
        )
    }
  }
}
