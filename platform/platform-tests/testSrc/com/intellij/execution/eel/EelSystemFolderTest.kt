// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.openapi.application.PathManager
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelArchiveApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelExecPosixApi
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.EelPosixProcess
import com.intellij.platform.eel.EelProcessManagementPosixApi
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.EelUserInfo
import com.intellij.platform.eel.EelUserPosixInfo
import com.intellij.platform.eel.EelUserWindowsInfo
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.utils.EelSystemFolderUtils
import com.intellij.testFramework.junit5.SystemPropertyClassLevel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.SystemProperties
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import kotlin.io.path.Path

// Keep expected paths independent of the product used to run the test.
private const val VENDOR = "EelSystemFolderTestVendor"

/**
 * Verifies that system folders use target-platform conventions and are correctly mapped under the non-local environment root.
 */
@OptIn(LowLevelLocalMachineAccess::class)
@TestApplication
@SystemPropertyClassLevel(propertyKey = "idea.vendor.name", propertyValue = VENDOR)
class EelSystemFolderTest {
  // PathManager caches the selector at class initialization, so use the value already selected for this test process.
  private val selector: String = PathManager.getPathsSelector() ?: "IJ-Platform"

  // This is a host-side mount path: UNC on Windows and an absolute POSIX path elsewhere.
  private val nonLocalRoot: Path =
    if (OS.CURRENT == OS.Windows) Path("""\\eel-test\Ubuntu""") else Path("/eel-test/Ubuntu")

  private data class Case(
    val name: String,
    val platform: EelPlatform,
    val home: String,
    val env: Map<String, String>,
    val expected: String,
  )

  @TestFactory
  fun `system folder of a non-local environment is a path of that environment`(): List<DynamicTest> {
    val cases = listOf(
      Case(
        name = "linux",
        platform = EelPlatform.Linux(EelPlatform.Arch.X86_64),
        home = "/home/user",
        env = emptyMap(),
        expected = "/home/user/.cache/$VENDOR/$selector",
      ),
      Case(
        name = "linux with XDG_CACHE_HOME",
        platform = EelPlatform.Linux(EelPlatform.Arch.X86_64),
        home = "/home/user",
        env = mapOf("XDG_CACHE_HOME" to "/var/tmp/cache"),
        expected = "/var/tmp/cache/$VENDOR/$selector",
      ),
      Case(
        name = "macOS",
        platform = EelPlatform.Darwin(EelPlatform.Arch.ARM_64),
        home = "/Users/user",
        env = emptyMap(),
        expected = "/Users/user/Library/Caches/$VENDOR/$selector",
      ),
      Case(
        name = "windows",
        platform = EelPlatform.Windows(EelPlatform.Arch.X86_64),
        home = """C:\Users\user""",
        env = emptyMap(),
        expected = """C:\Users\user\AppData\Local\$VENDOR\$selector""",
      ),
      Case(
        name = "windows with LOCALAPPDATA",
        platform = EelPlatform.Windows(EelPlatform.Arch.X86_64),
        home = """C:\Users\user""",
        env = mapOf("LOCALAPPDATA" to """D:\cache"""),
        expected = """D:\cache\$VENDOR\$selector""",
      ),
    )

    return cases.map { case ->
      dynamicTest(case.name) {
        val descriptor = FakeEelDescriptor(case.platform.osFamily, nonLocalRoot)
        val eel = FakeEelApi(descriptor, case.platform, EelPath.parse(case.home, descriptor), case.env)

        val systemFolder = EelSystemFolderUtils.getSystemFolder(eel)

        val expected = EelPath.parse(case.expected, descriptor)
        assertThat(systemFolder)
          .describedAs("the system folder must be `%s` inside %s", expected, descriptor.name)
          .isEqualTo(expected.asNioPath())
        assertThat(systemFolder.startsWith(nonLocalRoot))
          .describedAs("`%s` must not escape the environment root `%s` onto the host file system", systemFolder, nonLocalRoot)
          .isTrue()
      }
    }
  }

  /** Ensures that the fake descriptor maps target paths to distinct host paths. */
  @Test
  fun `a non-local environment addresses its home directory differently than the host does`() {
    val descriptor = FakeEelDescriptor(EelOsFamily.Posix, nonLocalRoot)
    val home = EelPath.parse("/home/user", descriptor)

    assertThat(home.asNioPath().toString()).isNotEqualTo(home.toString())
  }

  @Test
  fun `system folder of the local environment is a plain host path`() {
    val hostHome = Path(SystemProperties.getUserHome()).toAbsolutePath()
    val eel = FakeEelApi(
      descriptor = LocalEelDescriptor,
      platform = hostPlatform(),
      userHome = EelPath.parse(hostHome.toString(), LocalEelDescriptor),
      env = emptyMap(),
    )

    val systemFolder = EelSystemFolderUtils.getSystemFolder(eel)

    assertThat(systemFolder.isAbsolute).describedAs("`%s` must be absolute", systemFolder).isTrue()
    assertThat(systemFolder.startsWith(hostHome))
      .describedAs("`%s` must be located under the host home directory `%s`", systemFolder, hostHome)
      .isTrue()
    assertThat(systemFolder.endsWith(Path(VENDOR, selector)))
      .describedAs("`%s` must end with the vendor and the paths selector", systemFolder)
      .isTrue()
  }

  private fun hostPlatform(): EelPlatform = when (OS.CURRENT) {
    OS.Windows -> EelPlatform.Windows(EelPlatform.Arch.Unknown)
    OS.macOS -> EelPlatform.Darwin(EelPlatform.Arch.Unknown)
    OS.FreeBSD -> EelPlatform.FreeBSD(EelPlatform.Arch.Unknown)
    OS.Linux, OS.Other -> EelPlatform.Linux(EelPlatform.Arch.Unknown)
  }
}

private class FakeEelDescriptor(
  override val osFamily: EelOsFamily,
  override val rootPath: Path,
) : EelPathBoundDescriptor {
  override val name: String get() = "fake eel at $rootPath"
}

private class FakeEelApi(
  override val descriptor: EelDescriptor,
  override val platform: EelPlatform,
  private val userHome: EelPath,
  private val env: Map<String, String>,
) : EelApi {
  override val userInfo: EelUserInfo =
    when (platform.osFamily) {
      EelOsFamily.Posix -> object : EelUserPosixInfo {
        override val home: EelPath get() = userHome
        override val uid: Int get() = 0
        override val gid: Int get() = 0
      }
      EelOsFamily.Windows -> object : EelUserWindowsInfo {
        override val home: EelPath get() = userHome
      }
    }

  override val exec: EelExecApi = object : EelExecPosixApi {
    override val descriptor: EelDescriptor get() = this@FakeEelApi.descriptor

    override suspend fun fetchLoginShellEnvVariables(): Map<String, String> = env

    override suspend fun spawnProcess(generatedBuilder: EelExecApi.ExecuteProcessOptions): EelPosixProcess = unused("exec.spawnProcess")

    override fun environmentVariables(opts: EelExecApi.EnvironmentVariablesOptions): EelExecApi.EnvironmentVariablesDeferred =
      unused("exec.environmentVariables")

    override val processManagement: EelProcessManagementPosixApi get() = unused("exec.processManagement")

    override suspend fun getUserLoginShell(): EelPath = unused("exec.getUserLoginShell")

    override suspend fun findExeFilesInPath(binaryName: String): List<EelPath> = unused("exec.findExeFilesInPath")

    override suspend fun createExternalCli(options: EelExecApi.ExternalCliOptions): EelExecApi.ExternalCliEntrypoint =
      unused("exec.createExternalCli")
  }

  override val fs: EelFileSystemApi get() = unused("fs")
  override val tunnels: EelTunnelsApi get() = unused("tunnels")
  override val archive: EelArchiveApi get() = unused("archive")
}

private fun unused(what: String): Nothing =
  throw AssertionError("EelApi.$what is not implemented for EelSystemFolderTest")
