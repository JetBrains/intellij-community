// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream

/**
 * @see GradleDependencyResolver
 * @author Denes Daniel
 */
class GradleDependencyResolverTest {

  @field:TempDir
  lateinit var tempDir: File

  private fun testFile(name: String) = File(tempDir, name)

  @Test
  fun `choose auxiliary artifact file when there is none to choose from`() {
    val main = testFile("foo-bar.jar")
    assertThat(GradleDependencyResolver.chooseAuxiliaryArtifactFile(main, setOf())).isNull()
  }

  @Test
  fun `choose auxiliary artifact file when there is only one to choose from`() {
    val main = testFile("foo-bar.jar")
    val valid1 = testFile("foo-bar.src.jar")
    val valid2 = testFile("foo-bar-sources.jar")
    val valid3 = testFile("foo-bar-sources.src.jar")
    val nonsense = testFile("nonsense")
    assertThat(GradleDependencyResolver.chooseAuxiliaryArtifactFile(main, setOf(valid1))).isSameAs(valid1)
    assertThat(GradleDependencyResolver.chooseAuxiliaryArtifactFile(main, setOf(valid2))).isSameAs(valid2)
    assertThat(GradleDependencyResolver.chooseAuxiliaryArtifactFile(main, setOf(valid3))).isSameAs(valid3)
    assertThat(GradleDependencyResolver.chooseAuxiliaryArtifactFile(main, setOf(nonsense))).isSameAs(nonsense)
  }

  data class ChooseAuxiliaryTestCase(val main: String, val auxiliaries: Set<String>, val correctAuxiliary: String = main) {
    fun mapAuxiliaries(transform: (IndexedValue<String>) -> String): ChooseAuxiliaryTestCase {
      val auxiliariesMap = auxiliaries.withIndex().associate { it.value to transform(it) }
      return ChooseAuxiliaryTestCase(main, auxiliariesMap.values.toSet(), auxiliariesMap.getValue(correctAuxiliary))
    }
    fun mapMainAndAuxiliaries(transform: (String) -> String) = ChooseAuxiliaryTestCase(
      transform(main), auxiliaries.map(transform).toSet(), transform(correctAuxiliary))
    fun appendSuffixes(mainSuffix: String, auxiliarySuffix: String) = ChooseAuxiliaryTestCase(
      main + mainSuffix, auxiliaries.map { it + auxiliarySuffix }.toSet(), correctAuxiliary + auxiliarySuffix)
  }

  @ParameterizedTest
  @MethodSource("chooseAuxiliaryTestCases")
  fun `choose auxiliary artifact file when there are multiple to choose from`(testCase: ChooseAuxiliaryTestCase) {
    val main = testFile(testCase.main)
    val auxiliaries = testCase.auxiliaries.map(::testFile).toSet()
    val correctAuxiliary = testFile(testCase.correctAuxiliary)
    assertThat(GradleDependencyResolver.chooseAuxiliaryArtifactFile(main, auxiliaries)).isEqualTo(correctAuxiliary)
  }

  companion object {
    @JvmStatic
    fun chooseAuxiliaryTestCases(): Stream<ChooseAuxiliaryTestCase> {
      return listOf(
        ChooseAuxiliaryTestCase("net-soap", setOf("net", "net-soap", "net-soap-jaxb")),
        ChooseAuxiliaryTestCase("net-soap", setOf("net-soap", "net-soap-jaxb", "net")),
        ChooseAuxiliaryTestCase("net-soap", setOf("net-soap-jaxb", "net", "net-soap"))
      ).flatMap { input -> listOf(
        input,
        input.mapAuxiliaries { "${it.value}-x${"%02d".format(it.index + 1)}" })
      }.flatMap { input -> listOf(
        input,
        input.mapMainAndAuxiliaries { "${it}-1.2" },
        input.mapMainAndAuxiliaries { "${it}-3.x-3.4.5" })
      }.flatMap { input -> listOf(
        input,
        input.mapMainAndAuxiliaries { it.replace('-', '.') })
      }.flatMap { input -> listOf(
        input.appendSuffixes(".jar", ".src.jar"),
        input.appendSuffixes(".jar", "-sources.jar"),
        input.appendSuffixes(".jar", "-sources.src.jar"))
      }.stream()
    }
  }
}