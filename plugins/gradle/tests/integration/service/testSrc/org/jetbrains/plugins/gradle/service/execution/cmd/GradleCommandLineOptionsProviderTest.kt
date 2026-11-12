// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution.cmd

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test

class GradleCommandLineOptionsProviderTest {
  @Test
  fun `test supported options`() {
    val options = GradleCommandLineOptionsProvider.getSupportedOptions()

    // Debugging options
    assertEquals("full-stacktrace", options.getOption("S").longOpt)
    assertEquals("stacktrace", options.getOption("s").longOpt)

    // Performance options
    assertNotNull(options.getOption("build-cache"))
    assertNotNull(options.getOption("no-build-cache"))
    assertNotNull(options.getOption("configure-on-demand"))
    assertNotNull(options.getOption("no-configure-on-demand"))
    assertNotNull(options.getOption("max-workers"))
    assertNotNull(options.getOption("parallel"))
    assertNotNull(options.getOption("no-parallel"))
    assertNotNull(options.getOption("priority"))
    assertNotNull(options.getOption("profile"))
    assertNotNull(options.getOption("isolated-projects"))
    assertNotNull(options.getOption("no-isolated-projects"))

    // Logging options
    assertNotNull(options.getOption("console"))
    assertTrue(options.getOption("console-unicode").hasArg())
    assertNotNull(options.getOption("non-interactive"))

    // Develocity / Build Scan options
    assertNotNull(options.getOption("scan"))
    assertNotNull(options.getOption("no-scan"))
    assertTrue(options.getOption("develocity-url").hasArg())
    assertTrue(options.getOption("develocity-plugin-version").hasArg())

    // Diagnostic options
    assertNotNull(options.getOption("task-graph"))
    assertNotNull(options.getOption("problems-report"))
    assertNotNull(options.getOption("no-problems-report"))
    assertNotNull(options.getOption("property-upgrade-report"))

    // Logging options
    assertEquals("quiet", options.getOption("q").longOpt)
    assertEquals("warn", options.getOption("w").longOpt)
    assertEquals("info", options.getOption("i").longOpt)
    assertEquals("debug", options.getOption("d").longOpt)
    assertTrue(options.getOption("warning-mode").hasArg())

    // Execution options
    assertNotNull(options.getOption("include-build"))
    assertNotNull(options.getOption("offline"))
    assertNotNull(options.getOption("refresh-dependencies"))
    assertNotNull(options.getOption("dry-run"))
    assertNotNull(options.getOption("write-locks"))
    assertTrue(options.getOption("update-locks").hasArg())
    assertNotNull(options.getOption("no-rebuild"))

    // Environment options
    assertEquals("gradle-user-home", options.getOption("g").longOpt)
    assertEquals("project-dir", options.getOption("p").longOpt)
    assertTrue(options.getOption("project-cache-dir").hasArg())
    assertEquals("system-prop", options.getOption("D").longOpt)
    assertEquals("init-script", options.getOption("I").longOpt)
    assertEquals("project-prop", options.getOption("P").longOpt)

    // Executing tasks
    assertEquals("exclude-task", options.getOption("x").longOpt)
    assertNotNull(options.getOption("rerun-tasks"))
    assertNotNull(options.getOption("continue"))
    assertNotNull(options.getOption("no-continue"))

    // These options does not supported via tooling API.
    assertNull(options.getOption("help"))
    assertNull(options.getOption("version"))
    assertNull(options.getOption("show-version"))
  }

  @Test
  fun `test deprecated and unsupported options`() {
    val options = GradleCommandLineOptionsProvider.UNSUPPORTED_OPTIONS

    assertEquals("help", options.getOption("h").longOpt)
    assertEquals("version", options.getOption("v").longOpt)
    assertEquals("show-version", options.getOption("V").longOpt)
    assertEquals("build-file", options.getOption("b").longOpt)
    assertEquals("settings-file", options.getOption("c").longOpt)
  }
}