// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.eel

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightPlatformTestCase.closeAndDeleteProject
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.inspections.eel.TargetGeneralCommandLineInspection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class TargetGeneralCommandLineInspectionTest {
  private lateinit var myFixture: JavaCodeInsightTestFixture

  @Test
  fun `reports lost routing data in a constructor`() {
    @Language("Kt")
    val source = """
      import com.intellij.execution.configurations.GeneralCommandLine
      import com.intellij.platform.eel.path.EelPath

      fun start(executable: EelPath) {
        GeneralCommandLine(<warning descr="GeneralCommandLine cannot determine the process environment from this executable path">executable.toString()</warning>)
      }
    """.trimIndent()

    doTest(source)
  }

  @Test
  fun `reports lost routing data in withExePath`() {
    @Language("Kt")
    val source = """
      import com.intellij.execution.configurations.GeneralCommandLine
      import com.intellij.platform.eel.path.EelPath

      fun start(executable: EelPath) {
        <weak_warning descr="Consider using EelExecApi to create this process">GeneralCommandLine</weak_warning>()
          .withExePath(<warning descr="GeneralCommandLine cannot determine the process environment from this executable path">executable.toString()</warning>)
      }
    """.trimIndent()

    doTest(source)
  }

  @Test
  fun `suggests EelExecApi in a target context`() {
    @Language("Kt")
    val source = """
      import com.intellij.execution.configurations.GeneralCommandLine
      import com.intellij.platform.eel.EelApi

      fun start(eel: EelApi) {
        <weak_warning descr="Consider using EelExecApi to create this process">GeneralCommandLine</weak_warning>("git", "status")
        println(eel)
      }
    """.trimIndent()

    doTest(source)
  }

  @Test
  fun `does not add a migration warning to lost routing data`() {
    @Language("Kt")
    val source = """
      import com.intellij.execution.configurations.GeneralCommandLine
      import com.intellij.platform.eel.path.EelPath

      fun start(executable: EelPath) {
        GeneralCommandLine(<warning descr="GeneralCommandLine cannot determine the process environment from this executable path">executable.toString()</warning>)
      }
    """.trimIndent()

    doTest(source)
  }

  @Test
  fun `ignores a local context`() {
    @Language("Kt")
    val source = """
      import com.intellij.execution.configurations.GeneralCommandLine
      import com.intellij.platform.eel.LocalEelApi

      fun start(eel: LocalEelApi) {
        GeneralCommandLine("git", "status")
        println(eel)
      }
    """.trimIndent()

    doTest(source)
  }

  @Test
  fun `does not report an environment-aware NIO executable as an error`() {
    @Language("Kt")
    val source = """
      import com.intellij.execution.configurations.GeneralCommandLine
      import com.intellij.platform.eel.path.EelPath
      import com.intellij.platform.eel.provider.asNioPath
      import kotlin.io.path.pathString

      fun fill(commandLine: GeneralCommandLine, executable: EelPath) {
        commandLine.withExePath(executable.asNioPath().pathString)
      }
    """.trimIndent()

    doTest(source)
  }

  @BeforeEach
  fun setUpFixture() {
    val fixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createLightFixtureBuilder(projectDescriptor(), TargetGeneralCommandLineInspectionTest::class.simpleName!!)
      .getFixture()
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, LightTempDirTestFixtureImpl(true))
    myFixture.setUp()
    myFixture.enableInspections(TargetGeneralCommandLineInspection::class.java)
  }

  private fun doTest(source: String) = timeoutRunBlocking {
    val file = myFixture.configureByText("Example.kt", source)
    withContext(Dispatchers.EDT) {
      myFixture.openFileInEditor(file.virtualFile)
    }
    myFixture.testHighlighting()
  }

  @AfterEach
  fun tearDownFixture() {
    if (::myFixture.isInitialized) {
      myFixture.tearDown()
    }
    runBlocking(Dispatchers.EDT) {
      closeAndDeleteProject()
    }
  }

  private fun projectDescriptor(): LightProjectDescriptor = object : DefaultLightProjectDescriptor(IdeaTestUtil::getMockJdk21) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val kotlinStdlibName = "kotlin-stdlib"
      val kotlinStdlibPaths = IntelliJProjectConfiguration.getProjectLibrary(kotlinStdlibName)
      val kotlinStdlib = model.moduleLibraryTable.createLibrary(kotlinStdlibName)
      kotlinStdlib.modifiableModel.apply {
        for (rootUrl in kotlinStdlibPaths.classesUrls) addRoot(rootUrl, OrderRootType.CLASSES)
        for (rootUrl in kotlinStdlibPaths.sourcesUrls) addRoot(rootUrl, OrderRootType.SOURCES)
        commit()
      }

      for (directory in listOf(
          "platform/eel/src",
          "platform/eel-nioFs/src",
          "platform/platform-api/src",
          "platform/platform-util-io/src",
          "platform/util/src",
          "platform/util-rt/src",
        )) {
        PsiTestUtil.addSourceContentToRoots(
          module,
          Path.of(PathManager.getCommunityHomePath(), directory).refreshAndGetVirtualDirectory(),
        )
      }
    }
  }
}
