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
 * Defines filtering for Eel tests through the `eel.test.fixtures` system property.
 * Positive labels (`local`, `local-eel`, `local-ijent`, `docker`, `wsl`, `ssh`) include only the listed fixtures.
 * Labels prefixed with `-` (for example, `-docker`) exclude those fixtures from the enabled set.
 *
 * `@TestApplicationWithEel` and `eelFixtures` already use [instance] for filtering.
 */
@TestOnly
class EelFixtureFilter(
  val isLocalEelEnabled: Boolean = true,
  val isLocalIjentEnabled: Boolean = true,
  val isDockerEnabled: Boolean = true,
  val isWslEnabled: Boolean = true,
  /** No fixture reads this flag yet. With `ssh` alone in `eel.test.fixtures`, every fixture is filtered out. */
  val isSshEnabled: Boolean = true,
) {
  /**
   * The default case is optimized for local runs, where it's convenient to verify everything at once.
   * It can be not the best solution for CI, but can be OK for configurations with a few tests.
   */
  val isDefault: Boolean = isLocalEelEnabled && isLocalIjentEnabled && isDockerEnabled && isWslEnabled && isSshEnabled

  @TestOnly
  @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
  @ExtendWith(DockerEnabledCondition::class)
  annotation class OnlyWhenDockerEnabled

  @TestOnly
  @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
  @ExtendWith(WslEnabledCondition::class)
  annotation class OnlyWhenWslEnabled

  /**
   * For tests that verify behavior of code with [com.intellij.platform.eel.provider.localEel].
   */
  @TestOnly
  @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
  @ExtendWith(LocalEelEnabledCondition::class)
  annotation class OnlyWhenLocalEelEnabled

  /**
   * This annotation is for testing IJent itself. Maybe you need [OnlyWhenLocalEelEnabled] instead.
   */
  @TestOnly
  @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
  @ExtendWith(LocalIjentEnabledCondition::class)
  annotation class OnlyWhenLocalIjentEnabled

  override fun equals(other: Any?): Boolean =
    other is EelFixtureFilter &&
    isLocalEelEnabled == other.isLocalEelEnabled &&
    isLocalIjentEnabled == other.isLocalIjentEnabled &&
    isDockerEnabled == other.isDockerEnabled &&
    isWslEnabled == other.isWslEnabled &&
    isSshEnabled == other.isSshEnabled

  override fun hashCode(): Int =
    (if (isLocalIjentEnabled) 1 else 0) +
    (if (isLocalEelEnabled) 2 else 0) +
    (if (isDockerEnabled) 4 else 0) +
    (if (isWslEnabled) 8 else 0) +
    (if (isSshEnabled) 16 else 0)

  override fun toString(): String {
    return "EelFixtureFilter" +
           "(isLocalEelEnabled=$isLocalEelEnabled" +
           ", isLocalIjentEnabled=$isLocalIjentEnabled" +
           ", isDockerEnabled=$isDockerEnabled" +
           ", isWslEnabled=$isWslEnabled" +
           ", isSshEnabled=$isSshEnabled" +
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
      val rawLabels: List<String> = value.split(",").map(String::trim).filter(String::isNotEmpty)
      val (rawExclusions, rawInclusions) = rawLabels.partition { it.startsWith("-") }
      val inclusions = rawInclusions.toMutableSet()
      val exclusions = rawExclusions.toMutableSet()

      val defaultEnabled = inclusions.isEmpty()
      var isLocalEelEnabled = defaultEnabled
      var isLocalIjentEnabled = defaultEnabled
      var isDockerEnabled = defaultEnabled
      var isWslEnabled = defaultEnabled
      var isSshEnabled = defaultEnabled

      val setters: Map<String, (Boolean) -> Unit> = mapOf(
        "local" to { enabled -> isLocalIjentEnabled = enabled; isLocalEelEnabled = enabled },
        "local-eel" to { enabled -> isLocalEelEnabled = enabled },
        "local-ijent" to { enabled -> isLocalIjentEnabled = enabled },
        "docker" to { enabled -> isDockerEnabled = enabled },
        "wsl" to { enabled -> isWslEnabled = enabled },
        "ssh" to { enabled -> isSshEnabled = enabled },
      )
      for ((label, setter) in setters) {
        if (inclusions.remove(label)) {
          setter(true)
        }
      }
      for ((label, setter) in setters) {
        if (exclusions.remove("-$label")) {
          setter(false)
        }
      }
      val unknownLabels = inclusions + exclusions
      if (unknownLabels.isNotEmpty()) {
        val possibleLabels = setters.keys.sorted().joinToString()
        error("Unknown labels: ${unknownLabels.joinToString()}. Possible labels are: $possibleLabels (prefix with '-' to exclude)")
      }

      if (System.getProperty("ijent.test.skip.docker") != null) {
        isDockerEnabled = false
      }

      return EelFixtureFilter(
        isLocalEelEnabled = isLocalEelEnabled,
        isLocalIjentEnabled = isLocalIjentEnabled,
        isDockerEnabled = isDockerEnabled,
        isWslEnabled = isWslEnabled,
        isSshEnabled = isSshEnabled,
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

@TestOnly
private class LocalEelEnabledCondition : ExecutionCondition {
  override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
    return if (EelFixtureFilter.instance.isLocalEelEnabled) {
      ConditionEvaluationResult.enabled("Local Eel is enabled")
    }
    else {
      ConditionEvaluationResult.disabled("Local Eel is disabled")
    }
  }
}

@TestOnly
private class LocalIjentEnabledCondition : ExecutionCondition {
  override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
    return if (EelFixtureFilter.instance.isLocalIjentEnabled) {
      ConditionEvaluationResult.enabled("Local IJent is enabled")
    }
    else {
      ConditionEvaluationResult.disabled("Local IJent is disabled")
    }
  }
}