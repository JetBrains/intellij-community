// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.eel

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Definition of filtering for Eel tests. This class lets omitting running tests for specific eels,
 * i.e., without local Eel, without WSL, etc.
 *
 * `@TestApplicationWithEel` and `eelFixture` already use [instance] for filtering.
 */
@TestOnly
class EelFixtureFilter(
  val isLocalEelEnabled: Boolean = true,
  val isLocalIjentEnabled: Boolean = true,
  val isDockerEnabled: Boolean = true,
  val isWslEnabled: Boolean = true,
) {
  /**
   * The default case is optimized for local runs, where it's convenient to verify everything at once.
   * It can be not the best solution for CI, but can be OK for configurations with a few tests.
   */
  val isDefault: Boolean = isLocalEelEnabled && isLocalIjentEnabled && isDockerEnabled && isWslEnabled

  @TestOnly
  @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
  @ExtendWith(DockerEnabledCondition::class)
  annotation class OnlyWhenDockerEnabled

  @TestOnly
  @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
  @ExtendWith(WslEnabledCondition::class)
  annotation class OnlyWhenWslEnabled

  override fun equals(other: Any?): Boolean =
    other is EelFixtureFilter &&
    isLocalEelEnabled == other.isLocalEelEnabled &&
    isLocalIjentEnabled == other.isLocalIjentEnabled &&
    isDockerEnabled == other.isDockerEnabled &&
    isWslEnabled == other.isWslEnabled

  override fun hashCode(): Int =
    (if (isLocalIjentEnabled) 1 else 0) +
    (if (isLocalEelEnabled) 2 else 0) +
    (if (isDockerEnabled) 4 else 0) +
    (if (isWslEnabled) 8 else 0)

  override fun toString(): String {
    return "EelFixtureFilter" +
           "(isLocalEelEnabled=$isLocalEelEnabled" +
           ", isLocalIjentEnabled=$isLocalIjentEnabled" +
           ", isDockerEnabled=$isDockerEnabled" +
           ", isWslEnabled=$isWslEnabled" +
           ", isDefault=$isDefault" +
           ")"
  }

  companion object {
    @get:TestOnly
    var instance: EelFixtureFilter =
      run {
        val result = parse(System.getProperty("eel.test.fixtures", ""))
        logger<EelFixtureFilter>().info("From system properties: $result")
        result
      }
      private set

    @TestOnly
    fun replaceInstance(other: EelFixtureFilter): AutoCloseable {
      val original = instance
      instance = other
      return {
        instance = original
      }
    }

    @TestOnly
    internal fun parse(value: String): EelFixtureFilter {
      val labels: MutableSet<String> = value.split(",").map(String::trim).filter(String::isNotEmpty).toMutableSet()

      var isLocalEelEnabled = labels.isEmpty()
      var isLocalIjentEnabled = labels.isEmpty()
      var isDockerEnabled = labels.isEmpty()
      var isWslEnabled = labels.isEmpty()

      val setters: Map<String, () -> Unit> = mapOf(
        "local" to { isLocalIjentEnabled = true; isLocalEelEnabled = true },
        "local-eel" to { isLocalEelEnabled = true },
        "local-ijent" to { isLocalIjentEnabled = true },
        "docker" to { isDockerEnabled = true },
        "wsl" to { isWslEnabled = true },
      )
      for ((label, setter) in setters) {
        if (labels.remove(label)) {
          setter()
        }
      }
      if (labels.isNotEmpty()) {
        error("Unknown labels: ${labels.joinToString()}. Possible labels are: ${setters.keys.sorted().joinToString()}")
      }

      if (System.getProperty("ijent.test.skip.docker") != null) {
        isDockerEnabled = false
      }

      return EelFixtureFilter(
        isLocalEelEnabled = isLocalEelEnabled,
        isLocalIjentEnabled = isLocalIjentEnabled,
        isDockerEnabled = isDockerEnabled,
        isWslEnabled = isWslEnabled,
      )
    }
  }
}

@TestOnly
private class DockerEnabledCondition : ExecutionCondition {
  override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
    return if (EelFixtureFilter.instance.isDockerEnabled) {
      ConditionEvaluationResult.enabled("Docker is enabled")
    }
    else {
      ConditionEvaluationResult.disabled("Docker is disabled")
    }
  }
}

@TestOnly
private class WslEnabledCondition : ExecutionCondition {
  override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
    return if (SystemInfo.isWindows && EelFixtureFilter.instance.isWslEnabled) {
      ConditionEvaluationResult.enabled("WSL is enabled")
    }
    else {
      ConditionEvaluationResult.disabled("WSL is disabled")
    }
  }
}