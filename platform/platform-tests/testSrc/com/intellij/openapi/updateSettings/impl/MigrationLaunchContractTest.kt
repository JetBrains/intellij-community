// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.application.MigrationLaunchParameters
import com.intellij.openapi.application.MigrationLaunchReason
import com.intellij.openapi.application.macOpenCommand
import com.intellij.openapi.application.migrationLaunchArguments
import com.intellij.openapi.application.readMigrationLaunchParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Path

class MigrationLaunchContractTest {
  @Test
  fun `launch arguments have the version, the reason, the source product and the projects`() {
    val arguments = migrationLaunchArguments(MigrationLaunchReason.MIGRATE, "GO", listOf("/Users/me/my project", "/Users/me/other"))
    assertEquals(listOf(
      "-Dide.migration.version=1",
      "-Dide.migration.reason=migrate",
      "-Dide.migration.source.product=GO",
      "/Users/me/my project",
      "/Users/me/other",
    ), arguments)
  }

  @Test
  fun `launch arguments without projects`() {
    assertEquals(listOf("-Dide.migration.version=1", "-Dide.migration.reason=migrate", "-Dide.migration.source.product=RR"),
                 migrationLaunchArguments(MigrationLaunchReason.MIGRATE, "RR", emptyList()))
  }

  @Test
  fun `redirect launch arguments have the redirect reason`() {
    assertEquals(listOf("-Dide.migration.version=1", "-Dide.migration.reason=redirect", "-Dide.migration.source.product=RM", "/a"),
                 migrationLaunchArguments(MigrationLaunchReason.REDIRECT, "RM", listOf("/a")))
  }

  @Test
  fun `migrate round trip`() {
    val projects = listOf("/Users/me/my project", "/Users/me/other")
    for (parse in PARSERS) {
      val parsed = parse(migrationLaunchArguments(MigrationLaunchReason.MIGRATE, "GO", projects))
      assertEquals(MigrationLaunchParameters(MigrationLaunchReason.MIGRATE, "GO"), readMigrationLaunchParameters(parsed.properties::get))
      assertEquals(projects, parsed.positional)
    }
  }

  @Test
  fun `redirect round trip`() {
    val forwarded = listOf("diff", "/Users/me/a.txt", "/Users/me/b.txt", "--wait")
    for (parse in PARSERS) {
      val parsed = parse(migrationLaunchArguments(MigrationLaunchReason.REDIRECT, "RR", forwarded))
      assertEquals(MigrationLaunchParameters(MigrationLaunchReason.REDIRECT, "RR"), readMigrationLaunchParameters(parsed.properties::get))
      assertEquals(forwarded, parsed.positional)
    }
  }

  @Test
  fun `no parameters without a supported version`() {
    assertNull(read(VALID - "ide.migration.version"))
    assertNull(read(VALID + ("ide.migration.version" to "2")))
    assertNull(read(VALID + ("ide.migration.version" to "one")))
  }

  @Test
  fun `no parameters without a known reason`() {
    assertNull(read(VALID - "ide.migration.reason"))
    assertNull(read(VALID + ("ide.migration.reason" to "upgrade")))
  }

  @Test
  fun `no parameters without a source product`() {
    assertNull(read(VALID - "ide.migration.source.product"))
    assertNull(read(VALID + ("ide.migration.source.product" to " ")))
  }

  @Test
  fun `an unknown key does not change the parameters`() {
    assertEquals(MigrationLaunchParameters(MigrationLaunchReason.MIGRATE, "GO"), read(VALID + ("ide.migration.future" to "x")))
  }

  @Test
  fun `open command starts a new instance with separate arguments`() {
    val command = macOpenCommand(Path.of("/Users/me/Applications/Unified IDE.app"), listOf("-Da=b", "/Users/me/my project"))
    assertEquals(listOf("/usr/bin/open", "-n", "-a", "/Users/me/Applications/Unified IDE.app", "--args", "-Da=b", "/Users/me/my project"),
                 command)
  }
}

private val VALID = mapOf(
  "ide.migration.version" to "1",
  "ide.migration.reason" to "migrate",
  "ide.migration.source.product" to "GO",
)

private fun read(properties: Map<String, String>): MigrationLaunchParameters? = readMigrationLaunchParameters(properties::get)

private fun isPropertyArgument(arg: String): Boolean = arg.startsWith("-D") && arg.contains('=')

private class ParsedArguments(val properties: Map<String, String>, val positional: List<String>)

/** The two forms of `Main.preprocessArgs`: a `-D` argument in any position, and only the leading `-D` arguments. */
private val PARSERS: List<(List<String>) -> ParsedArguments> = listOf(
  { rawArgs ->
    val parts = rawArgs.partition(::isPropertyArgument)
    ParsedArguments(toProperties(parts.first), parts.second)
  },
  { rawArgs ->
    val propertyArgs = rawArgs.takeWhile(::isPropertyArgument)
    ParsedArguments(toProperties(propertyArgs), rawArgs.drop(propertyArgs.size))
  },
)

private fun toProperties(propertyArgs: List<String>): Map<String, String> =
  propertyArgs.associate {
    val option = it.removePrefix("-D")
    option.substringBefore('=') to option.substringAfter('=')
  }
