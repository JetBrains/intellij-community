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
import org.jetbrains.idea.devkit.inspections.eel.TargetSystemInfoInspection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class TargetSystemInfoInspectionTest {
  private lateinit var myFixture: JavaCodeInsightTestFixture

  @Test
  fun `reports host OS check with a target descriptor`() {
    @Language("Kt")
    val source = """
      import com.intellij.openapi.util.SystemInfo
      import com.intellij.platform.eel.EelDescriptor

      fun configure(descriptor: EelDescriptor) {
        if (SystemInfo.<warning descr="SystemInfo reports the IDE host OS, which may differ from the OS of the current environment">isWindows</warning>) println(descriptor)
      }
    """.trimIndent()

    doTest(source)
  }

  @Test
  fun `reports a Java host OS check with a target descriptor`() {
    @Language("Java")
    val source = """
      import com.intellij.openapi.util.SystemInfo;
      import com.intellij.platform.eel.EelDescriptor;

      class Example {
        void configure(EelDescriptor descriptor) {
          if (SystemInfo.<warning descr="SystemInfo reports the IDE host OS, which may differ from the OS of the current environment">isWindows</warning>) System.out.println(descriptor);
        }
      }
    """.trimIndent()

    doTest("Example.java", source)
  }

  @Test
  fun `mentions the project when a project parameter is available`() {
    @Language("Kt")
    val source = """
      import com.intellij.openapi.project.Project
      import com.intellij.openapi.util.SystemInfo
      import com.intellij.platform.eel.EelDescriptor

      fun configure(project: Project, descriptor: EelDescriptor) {
        if (SystemInfo.<warning descr="SystemInfo reports the IDE host OS, which may differ from the OS of this project">isWindows</warning>) println(project)
        println(descriptor)
      }
    """.trimIndent()

    doTest(source)
  }

  @Test
  fun `ignores host OS check without an EEL context`() {
    @Language("Kt")
    val source = """
      import com.intellij.openapi.util.SystemInfo

      fun configure() {
        if (SystemInfo.isWindows) println("Windows")
      }
    """.trimIndent()

    doTest(source)
  }

  @Test
  fun `ignores host OS check with a local descriptor`() {
    @Language("Kt")
    val source = """
      import com.intellij.openapi.util.SystemInfo
      import com.intellij.platform.eel.provider.LocalEelDescriptor

      fun configure(descriptor: LocalEelDescriptor) {
        if (SystemInfo.isWindows) println(descriptor)
      }
    """.trimIndent()

    doTest(source)
  }

  @BeforeEach
  fun setUpFixture() {
    val fixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createLightFixtureBuilder(projectDescriptor(), TargetSystemInfoInspectionTest::class.simpleName!!)
      .getFixture()
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, LightTempDirTestFixtureImpl(true))
    myFixture.setUp()
    myFixture.enableInspections(TargetSystemInfoInspection::class.java)
  }

  private fun doTest(source: String) = doTest("Example.kt", source)

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
        for (rootUrl in kotlinStdlibPaths.classesUrls) addRoot(rootUrl, OrderRootType.CLASSES)
        for (rootUrl in kotlinStdlibPaths.sourcesUrls) addRoot(rootUrl, OrderRootType.SOURCES)
        commit()
      }

      for (directory in listOf("platform/core-api/src", "platform/eel/src", "platform/util/src", "platform/util-rt/src", "platform/platform-api/src")) {
        PsiTestUtil.addSourceContentToRoots(
          module,
          Path.of(PathManager.getCommunityHomePath(), directory).refreshAndGetVirtualDirectory(),
        )
      }
    }
  }
}
