// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junitpioneer.jupiter.cartesian.ArgumentSets
import org.junitpioneer.jupiter.cartesian.CartesianTest

class GradleJVMIssuePatternMatcherTest {

  @ParameterizedTest(name = "suppliedJvmVersion: {0}")
  @ValueSource(ints = [8, 11, 15])
  fun `unresolved JUnit6 Gradle 6_0-6_1 test`(suppliedJvmVersion: Int) {
    val failureMessage = "Dependency resolution is looking for a library compatible with JVM runtime version $suppliedJvmVersion, " +
                         "but JUnit 6 is only compatible with JVM runtime version 17 or newer."

    val matchResult = GradleJVMIssuePatternMatcher.analyzeFailureMessage(failureMessage)

    assertNotNull(matchResult) { "Should recognize as a JVM version issue" }
    assertEquals(17, matchResult.requiredJvmVersion) { "Required JVM version should be 17" }
    assertEquals(failureMessage, matchResult.cleanedMessage) { "Message should not be cleaned" }
  }

  @CartesianTest(name = "{index} => suppliedJvmVersion: {0}, expectedJvmVersion: {1}, dependencyName: {2}")
  @CartesianTest.MethodFactory("unresolvedDependencyJvmIssueFactory")
  fun `unresolved Gradle 6_2-6_3 test`(suppliedJvmVersion: Int, expectedJvmVersion: Int, dependencyName: String) {
    val failureMessage = """
      Unable to find a matching variant of $dependencyName:
        - Variant 'apiElements' capability $dependencyName:
            - Incompatible attribute:
                - Required org.gradle.jvm.version '$suppliedJvmVersion' and found incompatible value '$expectedJvmVersion'.
            - Other attributes:
                - Required org.gradle.category 'library' and found compatible value 'library'.
                - Required org.gradle.dependency.bundling 'external' and found compatible value 'external'.
                - Required org.gradle.libraryelements 'classes' and found compatible value 'jar'.
                - Found org.gradle.status 'release' but wasn't required.
                - Required org.gradle.usage 'java-api' and found compatible value 'java-api'.
        - Variant 'javadocElements' capability $dependencyName:
            - Incompatible attribute:
                - Required org.gradle.category 'library' and found incompatible value 'documentation'.
            - Other attributes:
                - Required org.gradle.dependency.bundling 'external' and found compatible value 'external'.
                - Found org.gradle.docstype 'javadoc' but wasn't required.
                - Required org.gradle.jvm.version '$suppliedJvmVersion' but no value provided.
                - Required org.gradle.libraryelements 'classes' but no value provided.
                - Found org.gradle.status 'release' but wasn't required.
                - Required org.gradle.usage 'java-api' and found compatible value 'java-runtime'.
        - Variant 'runtimeElements' capability $dependencyName:
            - Incompatible attribute:
                - Required org.gradle.jvm.version '$suppliedJvmVersion' and found incompatible value '$expectedJvmVersion'.
            - Other attributes:
                - Required org.gradle.category 'library' and found compatible value 'library'.
                - Required org.gradle.dependency.bundling 'external' and found compatible value 'external'.
                - Required org.gradle.libraryelements 'classes' and found compatible value 'jar'.
                - Found org.gradle.status 'release' but wasn't required.
                - Required org.gradle.usage 'java-api' and found compatible value 'java-runtime'.
        - Variant 'sourcesElements' capability $dependencyName:
            - Incompatible attribute:
                - Required org.gradle.category 'library' and found incompatible value 'documentation'.
            - Other attributes:
                - Required org.gradle.dependency.bundling 'external' and found compatible value 'external'.
                - Found org.gradle.docstype 'sources' but wasn't required.
                - Required org.gradle.jvm.version '$suppliedJvmVersion' but no value provided.
                - Required org.gradle.libraryelements 'classes' but no value provided.
                - Found org.gradle.status 'release' but wasn't required.
                - Required org.gradle.usage 'java-api' and found compatible value 'java-runtime'.
    """.trimIndent()
    val expectedCleanedMessage = """
      Unable to find a matching variant of $dependencyName:
        - Variant 'apiElements' capability $dependencyName:
            - Incompatible attribute:
                - Required org.gradle.jvm.version '$suppliedJvmVersion' and found incompatible value '$expectedJvmVersion'.
        - Variant 'javadocElements' capability $dependencyName:
            - Incompatible attribute:
                - Required org.gradle.category 'library' and found incompatible value 'documentation'.
        - Variant 'runtimeElements' capability $dependencyName:
            - Incompatible attribute:
                - Required org.gradle.jvm.version '$suppliedJvmVersion' and found incompatible value '$expectedJvmVersion'.
        - Variant 'sourcesElements' capability $dependencyName:
            - Incompatible attribute:
                - Required org.gradle.category 'library' and found incompatible value 'documentation'.
    """.trimIndent()

    val matchResult = GradleJVMIssuePatternMatcher.analyzeFailureMessage(failureMessage)

    assertNotNull(matchResult) { "Should recognize as a JVM version issue" }
    assertEquals(expectedJvmVersion, matchResult.requiredJvmVersion) { "Required JVM version should be $expectedJvmVersion" }
    assertEquals(expectedCleanedMessage, matchResult.cleanedMessage) {
      "Cleaned message should not have unnecessary lines 'Other attributes:' and it's child items"
    }
  }

  @CartesianTest(name = "{index} => suppliedJvmVersion: {0}, expectedJvmVersion: {1}, dependencyName: {2}")
  @CartesianTest.MethodFactory("unresolvedDependencyJvmIssueFactory")
  fun `unresolved Gradle 6_4-6_9 test`(suppliedJvmVersion: Int, expectedJvmVersion: Int, dependencyName: String) {
    val failureMessage = """
      No matching variant of $dependencyName was found. The consumer was configured to find an API of a library compatible with Java $suppliedJvmVersion, preferably in the form of class files, and its dependencies declared externally but:
        - Variant 'apiElements' capability $dependencyName declares an API of a library, packaged as a jar, and its dependencies declared externally:
            - Incompatible because this component declares a component compatible with Java $expectedJvmVersion and the consumer needed a component compatible with Java $suppliedJvmVersion
        - Variant 'javadocElements' capability $dependencyName declares a runtime of a component, and its dependencies declared externally:
            - Incompatible because this component declares documentation and the consumer needed a library
            - Other compatible attributes:
                - Doesn't say anything about its target Java version (required compatibility with Java $suppliedJvmVersion)
                - Doesn't say anything about its elements (required them preferably in the form of class files)
        - Variant 'runtimeElements' capability $dependencyName declares a runtime of a library, packaged as a jar, and its dependencies declared externally:
            - Incompatible because this component declares a component compatible with Java $expectedJvmVersion and the consumer needed a component compatible with Java $suppliedJvmVersion
        - Variant 'sourcesElements' capability $dependencyName declares a runtime of a component, and its dependencies declared externally:
            - Incompatible because this component declares documentation and the consumer needed a library
            - Other compatible attributes:
                - Doesn't say anything about its target Java version (required compatibility with Java $suppliedJvmVersion)
                - Doesn't say anything about its elements (required them preferably in the form of class files)
    """.trimIndent()
    val expectedCleanedMessage = """
      No matching variant of $dependencyName was found. The consumer was configured to find an API of a library compatible with Java $suppliedJvmVersion, preferably in the form of class files, and its dependencies declared externally but:
        - Variant 'apiElements' capability $dependencyName declares an API of a library, packaged as a jar, and its dependencies declared externally:
            - Incompatible because this component declares a component compatible with Java $expectedJvmVersion and the consumer needed a component compatible with Java $suppliedJvmVersion
        - Variant 'javadocElements' capability $dependencyName declares a runtime of a component, and its dependencies declared externally:
            - Incompatible because this component declares documentation and the consumer needed a library
        - Variant 'runtimeElements' capability $dependencyName declares a runtime of a library, packaged as a jar, and its dependencies declared externally:
            - Incompatible because this component declares a component compatible with Java $expectedJvmVersion and the consumer needed a component compatible with Java $suppliedJvmVersion
        - Variant 'sourcesElements' capability $dependencyName declares a runtime of a component, and its dependencies declared externally:
            - Incompatible because this component declares documentation and the consumer needed a library
    """.trimIndent()

    val matchResult = GradleJVMIssuePatternMatcher.analyzeFailureMessage(failureMessage)

    assertNotNull(matchResult) { "Should recognize as a JVM version issue" }
    assertEquals(expectedJvmVersion, matchResult.requiredJvmVersion) { "Required JVM version should be $expectedJvmVersion" }
    assertEquals(expectedCleanedMessage, matchResult.cleanedMessage) {
      "Cleaned message should not have unnecessary lines 'Other compatible attributes:' and it's child items"
    }
  }

  @CartesianTest(name = "{index} => suppliedJvmVersion: {0}, expectedJvmVersion: {1}, dependencyName: {2}")
  @CartesianTest.MethodFactory("unresolvedDependencyJvmIssueFactory")
  fun `unresolved Gradle 7 test`(suppliedJvmVersion: Int, expectedJvmVersion: Int, dependencyName: String) {
    val failureMessage = """
      No matching variant of $dependencyName was found. The consumer was configured to find an API of a library compatible with Java $suppliedJvmVersion, preferably in the form of class files, preferably optimized for standard JVMs, and its dependencies declared externally but:
        - Variant 'apiElements' capability $dependencyName declares an API of a library, packaged as a jar, and its dependencies declared externally:
            - Incompatible because this component declares a component compatible with Java $expectedJvmVersion and the consumer needed a component compatible with Java $suppliedJvmVersion
            - Other compatible attribute:
                - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
        - Variant 'javadocElements' capability $dependencyName declares a runtime of a component, and its dependencies declared externally:
            - Incompatible because this component declares documentation and the consumer needed a library
            - Other compatible attributes:
                - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
                - Doesn't say anything about its target Java version (required compatibility with Java $suppliedJvmVersion)
                - Doesn't say anything about its elements (required them preferably in the form of class files)
        - Variant 'runtimeElements' capability $dependencyName declares a runtime of a library, packaged as a jar, and its dependencies declared externally:
            - Incompatible because this component declares a component compatible with Java $expectedJvmVersion and the consumer needed a component compatible with Java $suppliedJvmVersion
            - Other compatible attribute:
                - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
        - Variant 'sourcesElements' capability $dependencyName declares a runtime of a component, and its dependencies declared externally:
            - Incompatible because this component declares documentation and the consumer needed a library
            - Other compatible attributes:
                - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
                - Doesn't say anything about its target Java version (required compatibility with Java $suppliedJvmVersion)
                - Doesn't say anything about its elements (required them preferably in the form of class files)
    """.trimIndent()
    val expectedCleanedMessage = """
      No matching variant of $dependencyName was found. The consumer was configured to find an API of a library compatible with Java $suppliedJvmVersion, preferably in the form of class files, preferably optimized for standard JVMs, and its dependencies declared externally but:
        - Variant 'apiElements' capability $dependencyName declares an API of a library, packaged as a jar, and its dependencies declared externally:
            - Incompatible because this component declares a component compatible with Java $expectedJvmVersion and the consumer needed a component compatible with Java $suppliedJvmVersion
        - Variant 'javadocElements' capability $dependencyName declares a runtime of a component, and its dependencies declared externally:
            - Incompatible because this component declares documentation and the consumer needed a library
        - Variant 'runtimeElements' capability $dependencyName declares a runtime of a library, packaged as a jar, and its dependencies declared externally:
            - Incompatible because this component declares a component compatible with Java $expectedJvmVersion and the consumer needed a component compatible with Java $suppliedJvmVersion
        - Variant 'sourcesElements' capability $dependencyName declares a runtime of a component, and its dependencies declared externally:
            - Incompatible because this component declares documentation and the consumer needed a library
    """.trimIndent()

    val matchResult = GradleJVMIssuePatternMatcher.analyzeFailureMessage(failureMessage)

    assertNotNull(matchResult) { "Should recognize as a JVM version issue" }
    assertEquals(expectedJvmVersion, matchResult.requiredJvmVersion) { "Required JVM version should be $expectedJvmVersion" }
    assertEquals(expectedCleanedMessage, matchResult.cleanedMessage) {
      "Cleaned message should not have unnecessary lines 'Other compatible attributes:' and it's child items"
    }
  }

  @CartesianTest(name = "{index} => suppliedJvmVersion: {0}, expectedJvmVersion: {1}, dependencyName: {2}")
  @CartesianTest.MethodFactory("unresolvedDependencyJvmIssueFactory")
  fun `unresolved Gradle 8_0-8_6 test`(suppliedJvmVersion: Int, expectedJvmVersion: Int, dependencyName: String) {
    val failureMessage = """
      No matching variant of $dependencyName was found. The consumer was configured to find a library for use during compile-time, compatible with Java $suppliedJvmVersion, preferably in the form of class files, preferably optimized for standard JVMs, and its dependencies declared externally but:
        - Variant 'apiElements' capability $dependencyName declares a library for use during compile-time, packaged as a jar, and its dependencies declared externally:
            - Incompatible because this component declares a component, compatible with Java $expectedJvmVersion and the consumer needed a component, compatible with Java $suppliedJvmVersion
            - Other compatible attributes:
                - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
                - Doesn't say anything about org.jetbrains.kotlin.platform.type (required 'jvm')
        - Variant 'javadocElements' capability $dependencyName declares a component for use during runtime, and its dependencies declared externally:
            - Incompatible because this component declares documentation and the consumer needed a library
            - Other compatible attributes:
                - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
                - Doesn't say anything about its target Java version (required compatibility with Java $suppliedJvmVersion)
                - Doesn't say anything about its elements (required them preferably in the form of class files)
                - Doesn't say anything about org.jetbrains.kotlin.platform.type (required 'jvm')
        - Variant 'runtimeElements' capability $dependencyName declares a library for use during runtime, packaged as a jar, and its dependencies declared externally:
            - Incompatible because this component declares a component, compatible with Java $expectedJvmVersion and the consumer needed a component, compatible with Java $suppliedJvmVersion
            - Other compatible attributes:
                - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
                - Doesn't say anything about org.jetbrains.kotlin.platform.type (required 'jvm')
        - Variant 'sourcesElements' capability $dependencyName declares a component for use during runtime, and its dependencies declared externally:
            - Incompatible because this component declares documentation and the consumer needed a library
            - Other compatible attributes:
                - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
                - Doesn't say anything about its target Java version (required compatibility with Java $suppliedJvmVersion)
                - Doesn't say anything about its elements (required them preferably in the form of class files)
                - Doesn't say anything about org.jetbrains.kotlin.platform.type (required 'jvm')
    """.trimIndent()
    val expectedCleanedMessage = """
      No matching variant of $dependencyName was found. The consumer was configured to find a library for use during compile-time, compatible with Java $suppliedJvmVersion, preferably in the form of class files, preferably optimized for standard JVMs, and its dependencies declared externally but:
        - Variant 'apiElements' capability $dependencyName declares a library for use during compile-time, packaged as a jar, and its dependencies declared externally:
            - Incompatible because this component declares a component, compatible with Java $expectedJvmVersion and the consumer needed a component, compatible with Java $suppliedJvmVersion
        - Variant 'javadocElements' capability $dependencyName declares a component for use during runtime, and its dependencies declared externally:
            - Incompatible because this component declares documentation and the consumer needed a library
        - Variant 'runtimeElements' capability $dependencyName declares a library for use during runtime, packaged as a jar, and its dependencies declared externally:
            - Incompatible because this component declares a component, compatible with Java $expectedJvmVersion and the consumer needed a component, compatible with Java $suppliedJvmVersion
        - Variant 'sourcesElements' capability $dependencyName declares a component for use during runtime, and its dependencies declared externally:
            - Incompatible because this component declares documentation and the consumer needed a library
    """.trimIndent()

    val matchResult = GradleJVMIssuePatternMatcher.analyzeFailureMessage(failureMessage)

    assertNotNull(matchResult) { "Should recognize as a JVM version issue" }
    assertEquals(expectedJvmVersion, matchResult.requiredJvmVersion) { "Required JVM version should be $expectedJvmVersion" }
    assertEquals(expectedCleanedMessage, matchResult.cleanedMessage) {
      "Cleaned message should not have unnecessary lines 'Other compatible attributes:' and it's child items"
    }
  }

  @CartesianTest(name = "{index} => suppliedJvmVersion: {0}, expectedJvmVersion: {1}, dependencyName: {2}")
  @CartesianTest.MethodFactory("unresolvedDependencyJvmIssueFactory")
  fun `unresolved Gradle 8_7 test`(suppliedJvmVersion: Int, expectedJvmVersion: Int, dependencyName: String) {
    val failureMessage = """
      No matching variant of $dependencyName was found. The consumer was configured to find a library for use during compile-time, compatible with Java $suppliedJvmVersion, preferably in the form of class files, preferably optimized for standard JVMs, and its dependencies declared externally but:
        - Variant 'apiElements' declares a library for use during compile-time, packaged as a jar, and its dependencies declared externally:
            - Incompatible because this component declares a component, compatible with Java $expectedJvmVersion and the consumer needed a component, compatible with Java $suppliedJvmVersion
            - Other compatible attributes:
                - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
                - Doesn't say anything about org.jetbrains.kotlin.platform.type (required 'jvm')
        - Variant 'javadocElements' declares a component for use during runtime, and its dependencies declared externally:
            - Incompatible because this component declares documentation and the consumer needed a library
            - Other compatible attributes:
                - Doesn't say anything about its elements (required them preferably in the form of class files)
                - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
                - Doesn't say anything about its target Java version (required compatibility with Java $suppliedJvmVersion)
                - Doesn't say anything about org.jetbrains.kotlin.platform.type (required 'jvm')
        - Variant 'runtimeElements' declares a library for use during runtime, packaged as a jar, and its dependencies declared externally:
            - Incompatible because this component declares a component, compatible with Java $expectedJvmVersion and the consumer needed a component, compatible with Java $suppliedJvmVersion
            - Other compatible attributes:
                - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
                - Doesn't say anything about org.jetbrains.kotlin.platform.type (required 'jvm')
        - Variant 'sourcesElements' declares a component for use during runtime, and its dependencies declared externally:
            - Incompatible because this component declares documentation and the consumer needed a library
            - Other compatible attributes:
                - Doesn't say anything about its elements (required them preferably in the form of class files)
                - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
                - Doesn't say anything about its target Java version (required compatibility with Java $suppliedJvmVersion)
                - Doesn't say anything about org.jetbrains.kotlin.platform.type (required 'jvm')
    """.trimIndent()
    val expectedCleanedMessage = """
        No matching variant of $dependencyName was found. The consumer was configured to find a library for use during compile-time, compatible with Java $suppliedJvmVersion, preferably in the form of class files, preferably optimized for standard JVMs, and its dependencies declared externally but:
          - Variant 'apiElements' declares a library for use during compile-time, packaged as a jar, and its dependencies declared externally:
              - Incompatible because this component declares a component, compatible with Java $expectedJvmVersion and the consumer needed a component, compatible with Java $suppliedJvmVersion
          - Variant 'javadocElements' declares a component for use during runtime, and its dependencies declared externally:
              - Incompatible because this component declares documentation and the consumer needed a library
          - Variant 'runtimeElements' declares a library for use during runtime, packaged as a jar, and its dependencies declared externally:
              - Incompatible because this component declares a component, compatible with Java $expectedJvmVersion and the consumer needed a component, compatible with Java $suppliedJvmVersion
          - Variant 'sourcesElements' declares a component for use during runtime, and its dependencies declared externally:
              - Incompatible because this component declares documentation and the consumer needed a library
      """.trimIndent()

    val matchResult = GradleJVMIssuePatternMatcher.analyzeFailureMessage(failureMessage)

    assertNotNull(matchResult) { "Should recognize as a JVM version issue" }
    assertEquals(expectedJvmVersion, matchResult.requiredJvmVersion) { "Required JVM version should be $expectedJvmVersion" }
    assertEquals(expectedCleanedMessage, matchResult.cleanedMessage) {
      "Cleaned message should not have unnecessary lines 'Other compatible attributes:' and it's child items"
    }
  }

  @CartesianTest(name = "{index} => suppliedJvmVersion: {0}, expectedJvmVersion: {1}, dependencyName: {2}")
  @CartesianTest.MethodFactory("unresolvedDependencyJvmIssueFactory")
  fun `unresolved Gradle 8_8-8_14 test`(suppliedJvmVersion: Int, expectedJvmVersion: Int, dependencyName: String) {
    val failureMessage = "Dependency resolution is looking for a library compatible with JVM runtime version $suppliedJvmVersion, " +
                         "but '$dependencyName' is only compatible with JVM runtime version $expectedJvmVersion or newer."

    val matchResult = GradleJVMIssuePatternMatcher.analyzeFailureMessage(failureMessage)

    assertNotNull(matchResult) { "Should recognize as a JVM version issue" }
    assertEquals(expectedJvmVersion, matchResult.requiredJvmVersion) { "Required JVM version should be $expectedJvmVersion" }
    assertEquals(failureMessage, matchResult.cleanedMessage) { "Message should not be cleaned" }
  }

  companion object {
    @JvmStatic
    @Suppress("Unused") // Used by the CartesianTests
    fun unresolvedDependencyJvmIssueFactory(): ArgumentSets {
      return ArgumentSets
        .argumentsForFirstParameter((8..25).toList())
        .argumentsForNextParameter((8..25).toList())
        .argumentsForNextParameter(
          "org.junit.jupiter:junit-jupiter:6.0.0",
          "org.junit.jupiter:junit-jupiter:5.0.0",
          "group:name:",
          "group:name:version"
        )
    }
  }
}