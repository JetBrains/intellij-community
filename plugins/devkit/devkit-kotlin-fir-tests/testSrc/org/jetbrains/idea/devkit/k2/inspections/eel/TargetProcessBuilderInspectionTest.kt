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
import org.jetbrains.idea.devkit.inspections.eel.TargetProcessBuilderInspection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class TargetProcessBuilderInspectionTest {
  private lateinit var myFixture: JavaCodeInsightTestFixture

  @Test
  fun `reports a Kotlin EelPath argument`() {
    @Language("Kt")
    val source = """
      import com.intellij.platform.eel.path.EelPath

      fun start(executable: EelPath) {
        ProcessBuilder(<warning descr="ProcessBuilder starts a local process, but this argument refers to another environment">executable.toString()</warning>)
      }
    """.trimIndent()

    doTest("Example.kt", source)
  }

  @Test
  fun `reports a Java EelPath argument`() {
    @Language("Java")
    val source = """
      import com.intellij.platform.eel.path.EelPath;

      class Example {
        void start(EelPath executable) {
          new ProcessBuilder(<warning descr="ProcessBuilder starts a local process, but this argument refers to another environment">executable.toString()</warning>);
        }
      }
    """.trimIndent()

    doTest("Example.java", source)
  }

  @Test
  fun `reports target evidence through a variable`() {
    @Language("Kt")
    val source = """
      import com.intellij.platform.eel.path.EelPath

      fun start(executable: EelPath) {
        val command = executable.toString()
        ProcessBuilder(<warning descr="ProcessBuilder starts a local process, but this argument refers to another environment">command</warning>)
      }
    """.trimIndent()

    doTest("Example.kt", source)
  }

  @Test
  fun `reports target evidence in a command list`() {
    @Language("Kt")
    val source = """
      import com.intellij.platform.eel.path.EelPath

      fun start(executable: EelPath) {
        ProcessBuilder(<warning descr="ProcessBuilder starts a local process, but this argument refers to another environment">listOf(executable.toString())</warning>)
      }
    """.trimIndent()

    doTest("Example.kt", source)
  }

  @Test
  fun `ignores an unknown argument`() {
    @Language("Kt")
    val source = """
      fun start(executable: String) {
        ProcessBuilder(executable)
      }
    """.trimIndent()

    doTest("Example.kt", source)
  }

  @Test
  fun `ignores a local path argument`() {
    @Language("Kt")
    val source = """
      import com.intellij.platform.eel.annotations.LocalPath

      fun start(@LocalPath executable: String) {
        ProcessBuilder(executable)
      }
    """.trimIndent()

    doTest("Example.kt", source)
  }

  @Test
  fun `ignores an explicit local Eel argument`() {
    @Language("Kt")
    val source = """
      import com.intellij.platform.eel.provider.localEel

      fun start() {
        ProcessBuilder(localEel.descriptor.toString())
      }
    """.trimIndent()

    doTest("Example.kt", source)
  }

  @Test
  fun `supports standard suppression`() {
    @Language("Kt")
    val source = """
      import com.intellij.platform.eel.path.EelPath

      @Suppress("TargetProcessBuilder")
      fun start(executable: EelPath) {
        ProcessBuilder(executable.toString())
      }
    """.trimIndent()

    doTest("Example.kt", source)
  }

  @Test
  fun `reports mixed host and target arguments`() {
    @Language("Kt")
    val source = """
      import com.intellij.platform.eel.annotations.LocalPath
      import com.intellij.platform.eel.path.EelPath

      fun start(@LocalPath localArgument: String, targetArgument: EelPath) {
        ProcessBuilder(
          localArgument,
          <warning descr="ProcessBuilder combines arguments from different environments">targetArgument.toString()</warning>,
        )
      }
    """.trimIndent()

    doTest("Example.kt", source)
  }

  @Test
  fun `ignores a multi-routing path argument`() {
    @Language("Kt")
    val source = """
      import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath

      fun start(@MultiRoutingFileSystemPath executable: String) {
        ProcessBuilder(executable)
      }
    """.trimIndent()

    doTest("Example.kt", source)
  }

  @Test
  fun `ignores an EelPath after a local descriptor fail-fast guard`() {
    @Language("Kt")
    val source = """
      import com.intellij.platform.eel.path.EelPath
      import com.intellij.platform.eel.provider.LocalEelDescriptor

      fun start(executablePath: EelPath): ProcessBuilder {
        if (executablePath.descriptor != LocalEelDescriptor) {
          throw IllegalArgumentException("Only local execution is supported")
        }

        return ProcessBuilder(executablePath.toString())
      }
    """.trimIndent()

    doTest("Example.kt", source)
  }

  @Test
  fun `reports an EelPath after a non-terminating local descriptor check`() {
    @Language("Kt")
    val source = """
      import com.intellij.platform.eel.path.EelPath
      import com.intellij.platform.eel.provider.LocalEelDescriptor

      fun start(executablePath: EelPath) {
        if (executablePath.descriptor != LocalEelDescriptor) {
          println("Remote execution")
        }

        ProcessBuilder(<warning descr="ProcessBuilder starts a local process, but this argument refers to another environment">executablePath.toString()</warning>)
      }
    """.trimIndent()

    doTest("Example.kt", source)
  }

  @BeforeEach
  fun setUpFixture() {
    val fixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createLightFixtureBuilder(projectDescriptor(), TargetProcessBuilderInspectionTest::class.simpleName!!)
      .getFixture()
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, LightTempDirTestFixtureImpl(true))
    myFixture.setUp()
    myFixture.enableInspections(TargetProcessBuilderInspection::class.java)
  }

  private fun doTest(fileName: String, source: String) = timeoutRunBlocking {
    val file = myFixture.configureByText(fileName, source)
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
        for (rootUrl in kotlinStdlibPaths.classesUrls) {
          addRoot(rootUrl, OrderRootType.CLASSES)
        }
        for (rootUrl in kotlinStdlibPaths.sourcesUrls) {
          addRoot(rootUrl, OrderRootType.SOURCES)
        }
        commit()
      }

      for (directory in listOf("platform/eel/src",
                               "platform/eel-nioFs/src",
                               "platform/util/src",
                               "platform/util-rt/src",
                               "platform/platform-api/src")) {
        PsiTestUtil.addSourceContentToRoots(
          module,
          Path.of(PathManager.getCommunityHomePath(), directory).refreshAndGetVirtualDirectory(),
        )
      }
    }
  }
}
