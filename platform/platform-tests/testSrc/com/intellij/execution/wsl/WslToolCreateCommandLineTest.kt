// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.wsl.ijent.nio.toggle.WslEelDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.EelNioBridgeService
import com.intellij.platform.ijent.IjentExecFileProvider
import com.intellij.platform.ijent.impl.TestIjentExecFileProvider
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.fixtures.TestFixtureRule
import com.intellij.testFramework.registerServiceInstance
import com.intellij.tools.Tool
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.rules.RuleChain
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals


class WslToolCreateCommandLineTest {
  companion object {
    private val appRule = TestFixtureRule()
    private val wslRule = WslRule()

    @ClassRule
    @JvmField
    val ruleChain: RuleChain = RuleChain.outerRule(appRule).around(wslRule)

    var currAvailabilityService: WslIjentAvailabilityService? = null
    var currIjentManager: WslIjentManager? = null
    var currIjentExecFileProvider: IjentExecFileProvider? = null
    var disposable: Disposable? = null

    @BeforeClass
    @JvmStatic
    @OptIn(DelicateCoroutinesApi::class)
    fun enableIjent() {
      currAvailabilityService = WslIjentAvailabilityService.getInstance()
      currIjentManager = WslIjentManager.getInstance()
      currIjentExecFileProvider = runBlocking { IjentExecFileProvider.getInstance() }
      val app = ApplicationManagerEx.getApplicationEx()
      app.registerServiceInstance(
        WslIjentAvailabilityService::class.java,
        object : WslIjentAvailabilityService {
          override fun runWslCommandsViaIjent() = true
          override fun useIjentForWslNioFileSystem() = true
        })

      disposable = Disposer.newDisposable("WslToolCreateCommandLineTest")
      val newServiceScope = GlobalScope.childScope("Disposable $disposable", supervisor = true)
      Disposer.register(disposable!!) {
        newServiceScope.cancel(CancellationException("Disposed $disposable"))
      }

      app.registerServiceInstance(
        WslIjentManager::class.java,
        ProductionWslIjentManager(newServiceScope)
      )

      app.registerServiceInstance(
        IjentExecFileProvider::class.java,
        TestIjentExecFileProvider()
      )
    }

    @AfterClass
    @JvmStatic
    fun restoreServices() {
      ApplicationManagerEx.getApplicationEx().apply {
        currAvailabilityService?.let { registerServiceInstance(WslIjentAvailabilityService::class.java, it) }
        currIjentManager?.let { registerServiceInstance(WslIjentManager::class.java, it) }
        currIjentExecFileProvider?.let { registerServiceInstance(IjentExecFileProvider::class.java, it) }
      }
      disposable?.let { Disposer.dispose(it) }
    }

    private fun isMultiRoutingFSEnabled(): Boolean {
      return System.getProperty("java.nio.file.spi.DefaultFileSystemProvider") == "com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider"
    }

  }

  data class BridgeDescriptor(val service: EelNioBridgeService, val eelDescriptor: EelDescriptor)

  private fun setupBridgeService(wsl: WSLDistribution): BridgeDescriptor {
    val eelNioBridgeService = EelNioBridgeService.getInstanceSync()
    val localRoot = wsl.getWindowsPath("/")
    val descriptor = WslEelDescriptor(wsl)
    eelNioBridgeService.register(localRoot, descriptor, wsl.id, false, false) { _, previousFs ->
      previousFs
    }
    return BridgeDescriptor(eelNioBridgeService, descriptor)
  }

  private fun cleanupBridgeService(descriptor: BridgeDescriptor) {
    descriptor.service.unregister(descriptor.eelDescriptor)
  }

  @Rule
  @JvmField
  val progressJobRule = ProgressJobRule()

  @Test
  fun testCreateCommandLineWithWslPathAndMacroExpansion() {
    assumeTrue(isMultiRoutingFSEnabled())

    val wsl = wslRule.wsl
    val testUncPath = wsl.getWindowsPath("/etc/environment")
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(testUncPath)
    assertNotNull("Failed to find virtual file $testUncPath", virtualFile)

    val bridgeDescriptor = setupBridgeService(wsl)

    try {
      val dataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, null as Project?)
        .add(CommonDataKeys.VIRTUAL_FILE, virtualFile)
        .build()

      val tool = Tool()

      val programUncPath = wsl.getWindowsPath("/usr/bin/echo")

      tool.program = programUncPath
      tool.parameters = "\$FileDirName$ \$FileDir$"
      tool.workingDirectory = "\$FileDir$"

      val commandLine = tool.createCommandLine(dataContext)
      assertNotNull("Command line should not be null", commandLine)

      assertEquals("/usr/bin/echo", commandLine!!.exePath)

      val parameters = commandLine.parametersList.parameters
      assertEquals(listOf("etc", "/etc"), parameters)
    }
    finally {
      cleanupBridgeService(bridgeDescriptor)
    }
  }
}
