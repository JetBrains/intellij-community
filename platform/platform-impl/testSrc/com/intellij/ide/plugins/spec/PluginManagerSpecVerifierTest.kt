// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.spec

import com.intellij.platform.pluginManager.testFramework.PluginManagerSpecVerifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal class PluginManagerSpecVerifierTest {
  @TempDir
  lateinit var repositoryRoot: Path

  @Test
  fun `named test link resolves a method after a line break`() {
    writeFixture("expected behavior")

    assertThat(validate()).isEmpty()
  }

  @Test
  fun `each missing named test method is a separate violation`() {
    writeFixture("first missing method", "second missing method", declaredMethod = "different behavior")

    assertThat(validate()).containsExactlyInAnyOrder(
      "spec/example.spec.md:16: [@test] '../test/ExampleTest.kt' does not declare 'first missing method'",
      "spec/example.spec.md:16: [@test] '../test/ExampleTest.kt' does not declare 'second missing method'",
    )
  }

  @Test
  fun `configured domain section is required`() {
    writeFixture("expected behavior")

    assertThat(validate(REQUIRED_SECTIONS + "Source Loading")).containsExactly(
      "spec/example.spec.md: required section 'Source Loading' is missing",
    )
  }

  private fun writeFixture(vararg linkedMethods: String, declaredMethod: String = linkedMethods.single()) {
    write(
      "src/Example.kt",
      "// @spec spec/example.spec.md\nclass Example\n",
    )
    write(
      "test/ExampleTest.kt",
      "class ExampleTest { fun `$declaredMethod`() = Unit }\n",
    )
    write(
      "spec/example.spec.md",
      """
        ---
        name: Example
        description: Example behavior.
        targets:
          - ../src/Example.kt
        ---

        # Example

        Status: Active
        Date: 2026-09-17

        ## Purpose
        Summary.
        ## Verification
        [@test] ../test/ExampleTest.kt (
        ${linkedMethods.joinToString(";\n        ") { "  `$it`" }}
        )
        ## Open Questions
        Risks.
      """.trimIndent() + "\n",
    )
  }

  private fun validate(requiredSections: Set<String> = REQUIRED_SECTIONS): List<String> = PluginManagerSpecVerifier(
    repositoryRoot = repositoryRoot,
    specRoot = repositoryRoot.resolve("spec"),
    scanRoots = listOf(repositoryRoot.resolve("src"), repositoryRoot.resolve("test")),
    sectionRequirements = mapOf(
      "example.spec.md" to requiredSections,
    ),
  ).validate()

  private fun write(relativePath: String, text: String) {
    val path = repositoryRoot.resolve(relativePath)
    path.parent.createDirectories()
    path.writeText(text)
  }

  private companion object {
    val REQUIRED_SECTIONS = setOf("Purpose", "Verification", "Open Questions")
  }
}
