// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.fixtures.TestFixtureRule
import com.intellij.testFramework.registerServiceInstance
import com.intellij.tools.Tool
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import org.junit.AfterClass
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
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
    var disposable: Disposable? = null

    @BeforeClass
    @JvmStatic
    @OptIn(DelicateCoroutinesApi::class)
    fun enableIjent() {
      currAvailabilityService = WslIjentAvailabilityService.getInstance()
      currIjentManager = WslIjentManager.getInstance()
      ApplicationManagerEx.getApplicationEx().registerServiceInstance(
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

      ApplicationManagerEx.getApplicationEx().registerServiceInstance(
        WslIjentManager::class.java,
        ProductionWslIjentManager(newServiceScope)
      )
    }

    @AfterClass
    @JvmStatic
    fun restoreServices() {
      ApplicationManagerEx.getApplicationEx().apply {
        currAvailabilityService?.let { registerServiceInstance(WslIjentAvailabilityService::class.java, it) }
        currIjentManager?.let { registerServiceInstance(WslIjentManager::class.java, it) }
      }
      disposable?.let { Disposer.dispose(it) }
    }
  }

  @Rule
  @JvmField
  val progressJobRule = ProgressJobRule()


  @Test
  fun testCreateCommandLineWithWslPathAndMacroExpansion() {
    val wsl = wslRule.wsl
    val testUncPath = wsl.getWindowsPath("/etc/environment")
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(testUncPath)
    assertNotNull("Failed to find virtual file $testUncPath", virtualFile)

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
}
