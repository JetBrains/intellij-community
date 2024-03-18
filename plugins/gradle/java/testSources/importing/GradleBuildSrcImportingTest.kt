// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import junit.framework.AssertionFailedError
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.util.function.Consumer

class GradleBuildSrcImportingTest : GradleImportingTestCase() {

  @Test
  fun `test buildSrc project is imported as modules`() {
    createProjectSubFile("buildSrc/src/main/java/my/pack/TestPlugin.java",
                         """
                            package my.pack;
                            import org.gradle.api.Project;
                            import org.gradle.api.Plugin;
                            public class TestPlugin implements Plugin<Project> {
                              public void apply(Project project){};
                            }
                            """.trimIndent())
    importProject("apply plugin: 'java'\n" +
                  "apply plugin: my.pack.TestPlugin")
    assertModules("project", "project.main", "project.test",
                  "project.buildSrc", "project.buildSrc.main", "project.buildSrc.test")
    assertBuildScriptClassPathContains("project.main", listSourceFoldersOf("project.buildSrc.main"))
  }

  @Test
  fun `test buildSrc project with custom compiler out and not disabled delegation is imported`() {
    currentExternalProjectSettings.delegatedBuild = false
    createProjectSubFile("buildSrc/build.gradle",
                         """
                           apply plugin: 'idea'

                           idea.module {
                             outputDir = file("build/foo")
                             testOutputDir = file("build/bar")
                           }
                            """.trimIndent())
    importProject("apply plugin: 'java'\n")

    assertModuleOutput("project.buildSrc.main", "$projectPath/buildSrc/build/foo", "")
    assertModuleOutput("project.buildSrc.test", "", "$projectPath/buildSrc/build/bar")
  }


  @Test
  fun `test buildSrc project level dependencies are imported`() {
    val dependency = "junit:junit:4.12"
    val dependencyName = "Gradle: junit:junit:4.12"

    createBuildFile("buildSrc") {
      withMavenCentral()
      addTestImplementationDependency(dependency)
    }
    importProject("")
    assertModules("project",
                  "project.buildSrc", "project.buildSrc.main", "project.buildSrc.test")
    val moduleLibDeps = getModuleLibDeps("project.buildSrc.test", dependencyName)
    assertThat(moduleLibDeps).hasSize(1).allSatisfy(Consumer {
      assertThat(it.libraryLevel).isEqualTo("project")
    })
  }

  @Test
  fun `test explore files after double importing`() {
    createProjectSubFile("buildSrc/build.gradle", createBuildScriptBuilder().withJUnit4().generate())
    importProject("")
    importProject("")

    assertNoThrowable {
      ModuleManager.getInstance(myProject).modules.forEach { module ->
        ModuleRootManager.getInstance(module).orderEntries.forEach { entry ->
          entry.getFiles(OrderRootType.SOURCES)
        }
      }
    }
  }

  @TargetVersions("<6.0") // since 6.0 'buildSrc' is a reserved project name, https://docs.gradle.org/current/userguide/upgrading_version_5.html#buildsrc_is_now_reserved_as_a_project_and_subproject_build_name
  @Test
  fun `test buildSrc project is included into the main build`() {
    createProjectSubFile("buildSrc/src/main/java/my/pack/Util.java",
                         "package my.pack;\npublic class Util {}")

    importProject("apply plugin: 'java'")
    assertModules("project", "project.main", "project.test",
                  "project.buildSrc", "project.buildSrc.main", "project.buildSrc.test")

    createSettingsFile("include 'buildSrc'")
    importProject("apply plugin: 'java'")
    assertModules("project", "project.main", "project.test", "project.buildSrc")

  }

  @TargetVersions("6.7+") // since 6.7 included builds become "visible" for `buildSrc` project https://docs.gradle.org/6.7-rc-1/release-notes.html#build-src
  @Test
  fun `test buildSrc with applied plugins provided by included build of the root project`() {
    createProjectSubFile("buildSrc/build.gradle", "plugins { id 'myproject.my-test-plugin' }\n")
    createProjectSubFile("buildSrc/settings.gradle", "")
    val depJar = createProjectJarSubFile("buildSrc/libs/myLib.jar")
    createProjectSubFile("build-plugins/settings.gradle", "")
    createProjectSubFile("build-plugins/build.gradle", "plugins { id 'groovy-gradle-plugin' }\n")
    createProjectSubFile("build-plugins/src/main/groovy/myproject.my-test-plugin.gradle",
                         "plugins { id 'java' }\n" +
                         "dependencies { implementation files('libs/myLib.jar') }\n")

    createSettingsFile("includeBuild 'build-plugins'")
    importProject("")
    assertModules("project",
                  "project.buildSrc", "project.buildSrc.main", "project.buildSrc.test",
                  "build-plugins", "build-plugins.main", "build-plugins.test")

    assertModuleLibDep("project.buildSrc.main", depJar.presentableUrl, depJar.url)
  }

  /**
   * since 6.7 included builds become "visible" for `buildSrc` project https://docs.gradle.org/6.7-rc-1/release-notes.html#build-src
   * !!! Note, this is true only for builds included from the "root" build and it becomes visible also for "nested" `buildSrc` projects !!!
   * Transitive included builds are not visible even for related "transitive" `buildSrc` projects
   * due to limitation caused by specific ordering requirement:  "include order is important if an included build provides a plugin which should be discovered very very early".
   * It can be improved in the future Gradle releases.
   */
  @TargetVersions("6.7+")
  @Test
  fun `test nested buildSrc with applied plugins provided by included build of the root project`() {
    createProjectSubFile("build-plugins/settings.gradle", "")
    createProjectSubFile("build-plugins/build.gradle", "plugins { id 'groovy-gradle-plugin' }\n")
    createProjectSubFile("build-plugins/src/main/groovy/myproject.my-test-plugin.gradle",
                         "plugins { id 'java' }\n" +
                         "dependencies { implementation files('libs/myLib.jar') }\n")

    createProjectSubFile("another-build/settings.gradle", "")
    createProjectSubFile("another-build/buildSrc/build.gradle", "plugins { id 'myproject.my-test-plugin' }\n")
    createProjectSubFile("another-build/buildSrc/settings.gradle", "")
    val depJar = createProjectJarSubFile("another-build/buildSrc/libs/myLib.jar")

    createSettingsFile("includeBuild 'build-plugins'\n" +
                       "includeBuild 'another-build'")

    importProject("")
    assertModules("project",
                  "build-plugins", "build-plugins.main", "build-plugins.test",
                  "another-build", "another-build.buildSrc", "another-build.buildSrc.main", "another-build.buildSrc.test")

    assertModuleLibDep("another-build.buildSrc.main", depJar.presentableUrl, depJar.url)
  }


  /**
   * since 6.7 included builds become "visible" for `buildSrc` project https://docs.gradle.org/6.7-rc-1/release-notes.html#build-src
   * !!! Note, this is true only for builds included from the "root" build and it becomes visible also for "nested" `buildSrc` projects !!!
   * Check an edge case of transitive included builds  reaching the buildSrc. Such chain should be ignored, as it may cause failure with Gradle 7.2+
   * Related issue in Gradle's tracker: https://github.com/gradle/gradle/issues/20898
   */
  @TargetVersions("6.7+")
  @Test
  fun `test nested buildSrc with a transitive included builds chain reaching it`() {
    createProjectSubFile("build-plugins/settings.gradle", "")
    createProjectSubFile("build-plugins/build.gradle", "plugins { id 'groovy-gradle-plugin' }\n")
    createProjectSubFile("build-plugins/src/main/groovy/myproject.my-test-plugin.gradle",
                         "plugins { id 'java' }\n" +
                         "dependencies { implementation files('libs/myLib.jar') }\n")

    createProjectSubFile("another-build/settings.gradle", "")
    createProjectSubFile("another-build/buildSrc/build.gradle", "plugins { id 'myproject.my-test-plugin' }\n")
    createProjectSubFile("another-build/buildSrc/settings.gradle", "")
    val depJar = createProjectJarSubFile("another-build/buildSrc/libs/myLib.jar")

    createProjectSubFile("included-build/settings.gradle", "includeBuild '../another-build'")

    createSettingsFile("includeBuild 'build-plugins'\n" +
                       "includeBuild 'included-build'")

    importProject("")
    assertModules("project",
                  "build-plugins", "build-plugins.main", "build-plugins.test",
                  "included-build",
                  "another-build", "another-build.buildSrc", "another-build.buildSrc.main", "another-build.buildSrc.test")

    assertModuleLibDep("another-build.buildSrc.main", depJar.presentableUrl, depJar.url)
  }

  @TargetVersions("6.7+")
  @Test
  fun `test buildSrc project dependencies on projects of build included from the main build`() {
    createProjectSubFile("buildSrc/build.gradle", "plugins { id 'java' }\n" +
                                                  "dependencies {\n" +
                                                  "    implementation 'test:greeter'\n" +
                                                  "}")
    createProjectSubFile("buildSrc/settings.gradle", "")
    createProjectSubFile("buildSrc/src/main/java/my/GreetingTask.java", "package my;\n" +
                                                                        "import com.example.greeter.Greeter;\n" +
                                                                        "import org.gradle.api.DefaultTask;\n" +
                                                                        "import org.gradle.api.tasks.TaskAction;\n" +
                                                                        "import org.gradle.api.tasks.Input;\n" +
                                                                        "import org.gradle.api.provider.Property;\n" +
                                                                        "public abstract class GreetingTask extends DefaultTask {\n" +
                                                                        "    @Input\n" +
                                                                        "    public abstract Property<String> getGreeting();\n" +
                                                                        "    @TaskAction\n" +
                                                                        "    public void greet() {\n" +
                                                                        "        Greeter.greet(getGreeting().get());\n" +
                                                                        "    }\n" +
                                                                        "}\n")

    createProjectSubFile("another-build/settings.gradle", "rootProject.name = 'greeter'")
    createProjectSubFile("another-build/build.gradle", "plugins { id 'java' }\n" +
                                                       "group = 'test'")
    createProjectSubFile("another-build/src/main/java/com/example/greeter/Greeter.java", "package com.example.greeter;\n" +
                                                                        "\n" +
                                                                        "public class Greeter {\n" +
                                                                        "    public static void greet(String greeting) {\n" +
                                                                        "        System.out.println(greeting);\n" +
                                                                        "    }\n" +
                                                                        "}\n")

    createSettingsFile("rootProject.name = 'buildSrc-composite-dependency'\n" +
                       "includeBuild 'another-build'")
    importProject("import my.GreetingTask\n" +
                  "tasks.create('greet', GreetingTask) {\n" +
                  "    greeting.set(\"Hello\")\n" +
                  "}")
    assertModules("buildSrc-composite-dependency",
                  "buildSrc-composite-dependency.buildSrc", "buildSrc-composite-dependency.buildSrc.main", "buildSrc-composite-dependency.buildSrc.test",
                  "greeter", "greeter.main", "greeter.test")

    assertModuleModuleDeps("buildSrc-composite-dependency.buildSrc.main", "greeter.main")
    assertModuleModuleDepScope("buildSrc-composite-dependency.buildSrc.main", "greeter.main", DependencyScope.COMPILE)
  }

  @Test
  fun `test buildSrc with included projects name duplication`() {
    createSettingsFile("""
      includeBuild('build1')
      includeBuild('build2')
      """.trimIndent())
    createProjectSubFile("buildSrc/settings.gradle")

    createProjectSubFile("build1/settings.gradle", "include('app')")

    createProjectSubFile("build2/settings.gradle", "include('app')")
    createProjectSubFile("build2/buildSrc/build.gradle")

    importProject("")
    assertModules("project",
                  "project.buildSrc", "project.buildSrc.main", "project.buildSrc.test",
                  "build1", "build1.app",
                  "build2", "build2.app",
                  "build2.buildSrc", "build2.buildSrc.main", "build2.buildSrc.test")
  }


  @Test
  @TargetVersions("8.0+")
  fun `test composite members included in build Src are properly imported`() {
    //createProjectSubFile("gradle.properties", "org.gradle.jvmargs=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
    createSettingsFile("""
      rootProject.name = "A"
    """.trimIndent())

    createProjectSubFile("buildSrc/settings.gradle", "includeBuild('../buildSrcIncluded')")
    createProjectSubFile("buildSrcIncluded/settings.gradle", "rootProject.name='includedFromBuildSrc'")

    importProject("")
    assertModules("A",
                  "A.buildSrc", "A.buildSrc.test", "A.buildSrc.main",
                  "includedFromBuildSrc")
  }

  /*

  Builds inclusion and buildSrc presence graph

   A--> B--> D--> buildSrc
   |    └--> buildSrc
   |
   └--> C--> D--> buildSrc
        └--> buildSrc
   */
  @Test
  @TargetVersions("8.0+")
  fun `test buildSrc in a composite with build names duplication`() {
    createSettingsFile("""
      rootProject.name = "A"
      includeBuild("B")
      includeBuild("C")
    """.trimIndent())

    createProjectSubFile("B/settings.gradle", """
      rootProject.name = "B"
      includeBuild("D")
    """.trimIndent())

    createProjectSubFile("B/buildSrc/settings.gradle", "")

    createProjectSubFile("B/D/settings.gradle", "rootProject.name = 'D'")
    createProjectSubFile("B/D/buildSrc/settings.gradle", "")


    createProjectSubFile("C/settings.gradle", """
      rootProject.name = "C"
      includeBuild("D")
    """.trimIndent())

    createProjectSubFile("C/buildSrc/settings.gradle", "")

    createProjectSubFile("C/D/settings.gradle", "rootProject.name = 'D'")
    createProjectSubFile("C/D/buildSrc/settings.gradle", "")

    importProject("")
    assertModules("A", "B", "C", "D", "C.D",
                  "B.buildSrc", "B.buildSrc.main", "B.buildSrc.test",
                  "C.buildSrc", "C.buildSrc.main", "C.buildSrc.test",
                  "D.buildSrc", "D.buildSrc.main", "D.buildSrc.test",
                  "C.D.buildSrc", "C.D.buildSrc.main", "C.D.buildSrc.test")
  }

  private fun assertBuildScriptClassPathContains(moduleName: String, expectedEntries: Collection<VirtualFile>) {
    val module = ModuleManager.getInstance(myProject).findModuleByName(moduleName)
    val modulePath = ExternalSystemApiUtil.getExternalProjectPath(module)
                     ?: throw AssertionFailedError("Could not find external project path for module '$moduleName'")
    val entries = GradleBuildClasspathManager.getInstance(myProject).getModuleClasspathEntries(modulePath)
    assertThat(entries).containsAll(expectedEntries)
  }

  private fun listSourceFoldersOf(moduleName: String): Collection<VirtualFile> {
    val module = ModuleManager.getInstance(myProject).findModuleByName(moduleName)!!
    return ModuleRootManager.getInstance(module).sourceRoots.toList()
  }
}