// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.externalSystem.importing.ImportSpec
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.view.ExternalProjectsViewImpl
import com.intellij.openapi.externalSystem.view.ExternalSystemNode
import com.intellij.openapi.externalSystem.view.ProjectNode
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager.Companion.getInstance
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import groovy.util.Node
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runners.Parameterized
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicReference

@Suppress("SpellCheckingInspection")
open class GradleToolWindowTest : GradleImportingTestCase() {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "with Gradle-{0}")
    fun data(): MutableCollection<String?> {
      val result = ArrayList<String?>()
      result.add("5.0")
      return result
    }

    private fun buildTree(name: String?, nodes: Array<ExternalSystemNode<*>>, parent: Node?): Node {
      val node = Node(parent, name)
      for (n in nodes) {
        buildTree(n.getName(), n.getChildren(), node)
      }
      return node
    }
  }
  var toolWindow: ToolWindowHeadlessManagerImpl.MockToolWindow? = null
  lateinit var view: ExternalProjectsViewImpl
  var isPreview: Boolean = false

  public override fun setUp() {
    super.setUp()
    isPreview = false
    toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(myProject)
    view = ExternalProjectsViewImpl(myProject, toolWindow!!, externalSystemId)
    runInEdtAndWait {
      ExternalProjectsManagerImpl.getInstance(myProject).registerView(view)
      view.initStructure()
    }
  }

  @Test
  fun testSimpleBuild() {
    createSettingsFile("""
rootProject.name='rooot'
include ':child1'
include ':child2'
""".trimIndent())

    createProjectSubFile("build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())

    createProjectSubFile("../child1/build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())

    createProjectSubFile("../child2/build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())

    doTest()
  }

  @Test
  fun testDotInModuleName() {
    createSettingsFile("""
rootProject.name='rooot.dot'
include ':child1'
include ':child2'
include ':child2:dot.child'
""".trimIndent())

    createProjectSubFile("build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())

    createProjectSubFile("../child1/build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())
    createProjectSubFile("../child2/build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())

    createProjectSubFile("../child2/dot.child/build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())

    doTest()
  }

  @Test
  fun testBuildSrc() {
    createSettingsFile("""
rootProject.name='rooot'
include ':child1'
""".trimIndent())

    createProjectSubFile("build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())

    createProjectSubFile("../child1/build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
def foo = new testBuildSrcClassesUsages.BuildSrcClass().sayHello()
""".trimIndent())

    createProjectSubFile("buildSrc/src/main/groovy/testBuildSrcClassesUsages/BuildSrcClass.groovy", """
package testBuildSrcClassesUsages;
public class BuildSrcClass {
  public String sayHello() { 'Hello!' }
}
""".trimIndent())

    doTest()
  }


  @Test
  fun testSimpleBuildWithoutGrouping() {
    createSettingsFile("""
rootProject.name='rooot'
include ':child1'
include ':child2'
""".trimIndent())

    createProjectSubFile("build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())

    createProjectSubFile("../child1/build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())
    createProjectSubFile("../child2/build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())
    view.groupModules = false
    doTest()
  }


  @Test
  fun testWithExistedRootModule() {
    createMainModule()

    createSettingsFile("""
rootProject.name='rooot'
include ':child1'
include ':child2'
""".trimIndent())

    createProjectSubFile("build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())

    createProjectSubFile("../child1/build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())
    createProjectSubFile("../child2/build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())
    isPreview = true
    importProject()
    isPreview = false

    doTest()
    assert(getInstance(myProject).modules.size == 3)
  }

  @Test
  fun testWithExistedRootModuleWithoutPreviewImport() {
    createMainModule()

    createSettingsFile("""
rootProject.name='rooot'
include ':child1'
include ':child2'
""".trimIndent())

    createProjectSubFile("build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())

    createProjectSubFile("../child1/build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())

    createProjectSubFile("../child2/build.gradle", """
group 'test'
version '1.0-SNAPSHOT'
""".trimIndent())

    doTest()
    assert(getInstance(myProject).modules.size == 3)
  }

  fun createMainModule() {
    val module = AtomicReference<Module?>()
    runBlocking {
      edtWriteAction {
        val f = createProjectSubFile("project" + ".iml")
        module.set(getInstance(myProject).newModule(f.getPath(), JavaModuleType.getModuleType().getName()))
        PsiTestUtil.addContentRoot(module.get()!!, f.getParent())
      }
    }
  }

  @Test
  fun testBasicCompositeBuild() {
    createSettingsFile("""
rootProject.name='adhoc'
includeBuild '../my-app'
includeBuild '../my-utils'
""".trimIndent())

    createProjectSubFile("../my-app/settings.gradle", "rootProject.name = 'my-app'\n")
    createProjectSubFile("../my-app/build.gradle", """
apply plugin: 'java'
group 'org.sample'
version '1.0'

dependencies {
  compile 'org.sample:number-utils:1.0'
  compile 'org.sample:string-utils:1.0'
}
""".trimIndent())

    createProjectSubFile("../my-utils/settings.gradle", """
rootProject.name = 'my-utils'
include 'number-utils', 'string-utils'
""".trimIndent())

    createProjectSubFile("../my-utils/build.gradle", injectRepo("""
subprojects {
  apply plugin: 'java'

  group 'org.sample'
  version '1.0'
}

project(':string-utils') {
  dependencies {
    compile 'org.apache.commons:commons-lang3:3.4'
  }
}
""".trimIndent()))

    doTest()
  }

  @Test
  fun testDuplicatingProjectLeafNames() {
    createSettingsFile("""
rootProject.name = 'rootProject'
include 'p1', 'p2', 'p1:sub:sp1', 'p2:p2sub:sub:sp2'
include 'p1:leaf', 'p2:leaf'
""".trimIndent())

    doTest()
  }

  override fun createImportSpec(): ImportSpec {
    val importSpecBuilder = ImportSpecBuilder(super.createImportSpec())
    if (isPreview) {
      importSpecBuilder.usePreviewMode()
    }
    return importSpecBuilder.build()
  }

  private fun doTest() {
    importProject()
    checkToolWindowState()
  }

  private fun checkToolWindowState() {
    val data = ProjectDataManager.getInstance().getExternalProjectsData(myProject, externalSystemId).map { it.externalProjectStructure  }

    val rootNodes = data.map { ProjectNode(view, it) }

    val tree: Node = buildTree("View root", rootNodes.toTypedArray(), null)
    val sw = StringWriter()
    val writer = PrintWriter(sw)
    tree.print(writer)

    val path = this.path
    assert(File(path).exists()) { "File \$path doesn't exist" }

    assertSameLinesWithFile(path, sw.toString())
  }

  protected open val path: String
    get() {
      val communityPath = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/')
      var testName = getTestName(true)
      testName = getTestName(testName, true)
      return "$communityPath/plugins/gradle/java/testData/toolWindow/$testName.test"
    }
}
