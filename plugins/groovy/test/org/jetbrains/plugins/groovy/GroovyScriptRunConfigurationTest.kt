// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy

import com.intellij.execution.util.ScriptFileUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.jetbrains.plugins.groovy.bundled.bundledGroovyFile
import org.jetbrains.plugins.groovy.runner.DefaultGroovyScriptRunner
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfigurationType

internal class GroovyScriptRunConfigurationTest : LightGroovyTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = DESCRIPTOR

  fun testConfigureCommandLineUsesBundledGroovyWithoutGroovyModule() {
    val file = myFixture.configureByText("script.groovy", "println 'hi'")
    val configuration = GroovyScriptRunConfiguration(
      "script",
      myFixture.project,
      GroovyScriptRunConfigurationType().configurationFactory,
    ).apply {
      scriptPath = ScriptFileUtil.getScriptFilePath(file.virtualFile)
      module = null
    }

    val params = GroovyScriptRunConfiguration.createJavaParametersWithSdk(null)
    DefaultGroovyScriptRunner().configureCommandLine(params, null, false, file.virtualFile, configuration)

    assertEquals("org.codehaus.groovy.tools.GroovyStarter", params.mainClass)
    assertTrue(params.classPath.pathList.contains(bundledGroovyFile.get().path))
    assertTrue(params.programParametersList.parameters.containsAll(listOf("--main", "groovy.ui.GroovyMain")))
  }

  companion object {
    private val DESCRIPTOR = DefaultLightProjectDescriptor()
  }
}
