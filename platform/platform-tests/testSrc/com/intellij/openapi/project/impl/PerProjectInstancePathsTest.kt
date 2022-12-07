// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals

class PerProjectInstancePathsTest : BareTestFixtureTestCase() {

  @Test
  fun systemDirInsideChildProcessWithOpenedProject() {
    val currentProject = "/home/user/workspace/project"
    val newProject = "/home/user/modern-workspace/startup"

    val perProjectSuffix = "/home/user/ij/system/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val currentPerProject = "home/user/workspace/project"
    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$perProjectSuffix/$newPerProject"),
      PerProjectInstancePaths.getSystemDir(
        Paths.get("$perProjectSuffix/$currentPerProject"),
        true,
        { Paths.get(currentProject) },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun systemDirInsideChildProcessWithNoProject() {
    val newProject = "/home/user/modern-workspace/startup"

    val perProjectSuffix = "/home/user/ij/system/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val currentPerProject = "home/user/workspace/project"
    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$perProjectSuffix/$newPerProject"),
      PerProjectInstancePaths.getSystemDir(
        Paths.get("$perProjectSuffix/$currentPerProject"),
        true,
        { null },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun systemDirInsideBaseProcess() {
    val newProject = "/home/user/modern-workspace/startup"

    val base = "/home/user/ij/system"
    val perProjectSuffix = "$base/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$perProjectSuffix/$newPerProject"),
      PerProjectInstancePaths.getSystemDir(
        Paths.get(base),
        false,
        { throw IllegalStateException() },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun configDirInsideChildProcessWithOpenedProject() {
    val currentProject = "/home/user/workspace/project"
    val newProject = "/home/user/modern-workspace/startup"

    val perProjectSuffix = "/home/user/ij/config/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val currentPerProject = "home/user/workspace/project"
    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$perProjectSuffix/$newPerProject"),
      PerProjectInstancePaths.getConfigDir(
        Paths.get("$perProjectSuffix/$currentPerProject"),
        true,
        { Paths.get(currentProject) },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun configDirInsideChildProcessWithNoProject() {
    val newProject = "/home/user/modern-workspace/startup"

    val perProjectSuffix = "/home/user/ij/config/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val currentPerProject = "home/user/workspace/project"
    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$perProjectSuffix/$newPerProject"),
      PerProjectInstancePaths.getConfigDir(
        Paths.get("$perProjectSuffix/$currentPerProject"),
        true,
        { null },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun configDirInsideBaseProcess() {
    val newProject = "/home/user/modern-workspace/startup"

    val base = "/home/user/ij/config"
    val perProjectSuffix = "$base/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$perProjectSuffix/$newPerProject"),
      PerProjectInstancePaths.getConfigDir(
        Paths.get(base),
        false,
        { throw IllegalStateException() },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun pluginsDirInsideChildProcessWithOpenedProject() {
    val currentProject = "/home/user/workspace/project"
    val newProject = "/home/user/modern-workspace/startup"

    val pluginsPerProjectSuffix = "/home/user/ij/plugins/${ProjectManagerEx.PER_PROJECT_SUFFIX}"
    val configPerProjectSuffix = "/home/user/ij/config/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val currentPerProject = "home/user/workspace/project"
    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$pluginsPerProjectSuffix/$newPerProject"),
      PerProjectInstancePaths.getPluginsDir(
        Paths.get("$pluginsPerProjectSuffix/$currentPerProject"),
        Paths.get("$configPerProjectSuffix/$currentPerProject"),
        true,
        { Paths.get(currentProject) },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun pluginsDirInsideChildProcessWithNoProject() {
    val newProject = "/home/user/modern-workspace/startup"

    val pluginsPerProjectSuffix = "/home/user/ij/plugins/${ProjectManagerEx.PER_PROJECT_SUFFIX}"
    val configPerProjectSuffix = "/home/user/ij/config/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val currentPerProject = "home/user/workspace/project"
    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$pluginsPerProjectSuffix/$newPerProject"),
      PerProjectInstancePaths.getPluginsDir(
        Paths.get("$pluginsPerProjectSuffix/$currentPerProject"),
        Paths.get("$configPerProjectSuffix/$currentPerProject"),
        true,
        { null },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun pluginsDirInsideBaseProcess() {
    val newProject = "/home/user/modern-workspace/startup"

    val basePlugins = "/home/user/ij/plugins"
    val baseConfig = "/home/user/ij/config"

    val pluginsPerProjectSuffix = "$basePlugins/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$pluginsPerProjectSuffix/$newPerProject"),
      PerProjectInstancePaths.getPluginsDir(
        Paths.get(basePlugins),
        Paths.get(baseConfig),
        false,
        { throw IllegalStateException() },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun configPluginsDirInsideChildProcessWithOpenedProject() {
    val currentProject = "/home/user/workspace/project"
    val newProject = "/home/user/modern-workspace/startup"

    val configPerProjectSuffix = "/home/user/ij/config/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val currentPerProject = "home/user/workspace/project"
    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$configPerProjectSuffix/$newPerProject/plugins"),
      PerProjectInstancePaths.getPluginsDir(
        Paths.get("$configPerProjectSuffix/$currentPerProject/plugins"),
        Paths.get("$configPerProjectSuffix/$currentPerProject"),
        true,
        { Paths.get(currentProject) },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun configPluginsDirInsideChildProcessWithNoProject() {
    val newProject = "/home/user/modern-workspace/startup"

    val configPerProjectSuffix = "/home/user/ij/config/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val currentPerProject = "home/user/workspace/project"
    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$configPerProjectSuffix/$newPerProject/plugins"),
      PerProjectInstancePaths.getPluginsDir(
        Paths.get("$configPerProjectSuffix/$currentPerProject/plugins"),
        Paths.get("$configPerProjectSuffix/$currentPerProject"),
        true,
        { null },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun configPluginsDirInsideBaseProcess() {
    val newProject = "/home/user/modern-workspace/startup"

    val base = "/home/user/ij/config"
    val configPerProjectSuffix = "$base/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$configPerProjectSuffix/$newPerProject/plugins"),
      PerProjectInstancePaths.getPluginsDir(
        Paths.get("$base/plugins"),
        Paths.get(base),
        false,
        { throw IllegalStateException() },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun logDirInsideChildProcessWithOpenedProject() {
    val currentProject = "/home/user/workspace/project"
    val newProject = "/home/user/modern-workspace/startup"

    val logPerProjectSuffix = "/home/user/ij/log/${ProjectManagerEx.PER_PROJECT_SUFFIX}"
    val systemPerProjectSuffix = "/home/user/ij/system/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val currentPerProject = "home/user/workspace/project"
    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$logPerProjectSuffix/$newPerProject"),
      PerProjectInstancePaths.getLogDir(
        Paths.get("$logPerProjectSuffix/$currentPerProject"),
        Paths.get("$systemPerProjectSuffix/$currentPerProject"),
        true,
        { Paths.get(currentProject) },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun logDirInsideChildProcessWithNoProject() {
    val newProject = "/home/user/modern-workspace/startup"

    val logPerProjectSuffix = "/home/user/ij/log/${ProjectManagerEx.PER_PROJECT_SUFFIX}"
    val systemPerProjectSuffix = "/home/user/ij/system/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val currentPerProject = "home/user/workspace/project"
    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$logPerProjectSuffix/$newPerProject"),
      PerProjectInstancePaths.getLogDir(
        Paths.get("$logPerProjectSuffix/$currentPerProject"),
        Paths.get("$systemPerProjectSuffix/$currentPerProject"),
        true,
        { null },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun logDirInsideBaseProcess() {
    val newProject = "/home/user/modern-workspace/startup"

    val baseLog = "/home/user/ij/log"
    val baseSystem = "/home/user/ij/system"

    val logPerProjectSuffix = "$baseLog/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$logPerProjectSuffix/$newPerProject"),
      PerProjectInstancePaths.getLogDir(
        Paths.get(baseLog),
        Paths.get(baseSystem),
        false,
        { throw IllegalStateException() },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun systemLogDirInsideChildProcessWithOpenedProject() {
    val currentProject = "/home/user/workspace/project"
    val newProject = "/home/user/modern-workspace/startup"

    val systemPerProjectSuffix = "/home/user/ij/system/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val currentPerProject = "home/user/workspace/project"
    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$systemPerProjectSuffix/$newPerProject/log"),
      PerProjectInstancePaths.getLogDir(
        Paths.get("$systemPerProjectSuffix/$currentPerProject/log"),
        Paths.get("$systemPerProjectSuffix/$currentPerProject"),
        true,
        { Paths.get(currentProject) },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun systemLogDirInsideChildProcessWithNoProject() {
    val newProject = "/home/user/modern-workspace/startup"

    val systemPerProjectSuffix = "/home/user/ij/system/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val currentPerProject = "home/user/workspace/project"
    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$systemPerProjectSuffix/$newPerProject/log"),
      PerProjectInstancePaths.getLogDir(
        Paths.get("$systemPerProjectSuffix/$currentPerProject/log"),
        Paths.get("$systemPerProjectSuffix/$currentPerProject"),
        true,
        { null },
        Paths.get(newProject)
      )
    )
  }

  @Test
  fun systemLogDirInsideBaseProcess() {
    val newProject = "/home/user/modern-workspace/startup"

    val base = "/home/user/ij/system"
    val systemPerProjectSuffix = "$base/${ProjectManagerEx.PER_PROJECT_SUFFIX}"

    val newPerProject = "home/user/modern-workspace/startup"

    assertEquals(
      Paths.get("$systemPerProjectSuffix/$newPerProject/log"),
      PerProjectInstancePaths.getLogDir(
        Paths.get("$base/log"),
        Paths.get(base),
        false,
        { throw IllegalStateException() },
        Paths.get(newProject)
      )
    )
  }
}