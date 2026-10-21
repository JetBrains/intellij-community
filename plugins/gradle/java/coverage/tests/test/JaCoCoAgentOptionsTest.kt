// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.coverage

import com.intellij.util.execution.ParametersListUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Checks how the IDE detects its own JaCoCo agent.
 *
 * The init script for the Gradle `jacoco` plugin runs only for the JaCoCo coverage runner.
 * [hasJaCoCoAgent] tells that runner from every other one.
 */
class JaCoCoAgentOptionsTest {

  @Test
  fun `test agent of the JaCoCo coverage runner`() {
    val hasAgent = hasAgent(
      $$"-javaagent:/tmp/jacocoagent1.jar=destfile=/system/coverage/project$Test.exec,append=false," +
      "includes=org.example.*:com.example.*,excludes=org.example.Generated"
    )
    assertThat(hasAgent).isTrue()
  }

  @Test
  fun `test agent of the JaCoCo coverage runner without filters`() {
    assertThat(hasAgent("-javaagent:/tmp/jacocoagent1.jar=destfile=/system/coverage/project.exec,append=false")).isTrue()
  }

  @Test
  fun `test agent with classes with no location`() {
    val hasAgent = hasAgent(
      "-javaagent:/tmp/jacocoagent1.jar=destfile=/system/coverage/project.exec,append=false,inclnolocationclasses=true"
    )
    assertThat(hasAgent).isTrue()
  }

  @Test
  fun `test destination with a space`() {
    val destinationFile = "/home/John Doe/system/coverage/project.exec"
    assertThat(hasAgent("-javaagent:/tmp/jacocoagent1.jar=destfile=$destinationFile,append=false")).isTrue()
  }

  @Test
  fun `test agent of the IntelliJ coverage runner`() {
    // The IntelliJ runner passes an options file, and that agent does not clash with the Gradle one.
    assertThat(hasAgent("-javaagent:/idea/lib/intellij-coverage-agent-1.0.796.jar=/tmp/coverage1.args")).isFalse()
  }

  @Test
  fun `test run without an agent`() {
    assertThat(hasAgent("-Xmx512m", "-Dfoo=bar")).isFalse()
    assertThat(hasJaCoCoAgent(null)).isFalse()
    assertThat(hasJaCoCoAgent("")).isFalse()
  }

  @Test
  fun `test agent among other parameters`() {
    val hasAgent = hasAgent(
      "-Xmx512m",
      "-javaagent:/tmp/jacocoagent1.jar=destfile=/system/coverage/project.exec,append=false",
      "-Dfoo=bar"
    )
    assertThat(hasAgent).isTrue()
  }

  /**
   * Joins [parameters] the way the IDE does, so that a parameter with a space keeps its quotes.
   *
   * @see com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState
   */
  private fun hasAgent(vararg parameters: String): Boolean {
    return hasJaCoCoAgent(ParametersListUtil.join(*parameters))
  }
}
