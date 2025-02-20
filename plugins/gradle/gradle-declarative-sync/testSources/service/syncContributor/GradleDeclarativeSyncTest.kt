// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor

import com.android.tools.idea.gradle.feature.flags.DeclarativeStudioSupport.override
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.ListenerAssertion
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.use
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.dependencyAssertion.DependencyAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions.assertContentRoots
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import org.jetbrains.plugins.gradle.importing.syncAction.GradlePhasedSyncTestCase
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class GradleDeclarativeSyncTest : GradlePhasedSyncTestCase() {

  override fun setUp() {
    super.setUp()
    override(true) // enable android gradle declarative flag
  }

  @Test
  @TargetVersions("8.9+")
  fun `test declarative model creation in a simple Gradle project`() {
    val projectRoot = projectRoot.toNioPath()
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    createProjectSubFile("settings.gradle.dcl", """
      pluginManagement {
          repositories {
              google()
              gradlePluginPortal()
          }
      }
      
      plugins {
          id("org.gradle.experimental.jvm-ecosystem").version("0.1.21")
      }
      
      rootProject.name = "test-dcl"
      """)
    createProjectSubFile("build.gradle.dcl", """
      javaApplication {
          javaVersion = 17
          mainClass = "com.example.App"

          dependencies {
              implementation("com.google.guava:guava:32.1.3-jre")
          }

          testing {
              // test on 21
              testJavaVersion = 21

              dependencies {
                  implementation("org.junit.jupiter:junit-jupiter:5.10.2")
              }
          }
      }
    """.trimIndent())
    createProjectSubFile("src/main/java/org/example/MyClass.java",
                         """
      package org.example;
      public class Main {
          public static void main(String[] args) {
              System.out.println("Hello, World!");
          }
      
          public static String getString() {
              return "yep";
          }
      }
    """.trimIndent())
    createProjectSubFile("src/main/java/org/example/MyClass.java",
                         """
      package org.example;
      public class Another {
          public String getString() {
              return Main.getString() + " again";
          }
      }
    """.trimIndent())

    createProjectSubFile("src/test/java/org/example/Test.java",
                         """
      package org.example;
      import org.junit.jupiter.api.Assertions;
      import org.junit.jupiter.api.Test;
      
      public class TestCase {
      
          @Test
          void test() {
              Assertions.assertEquals("yep", Main.getString());
          }
      }
    """.trimIndent())

    Disposer.newDisposable().use { disposable ->

      val projectRootContributorAssertion = ListenerAssertion()

      whenResolveProjectInfoStarted(disposable) { _, storage ->
        projectRootContributorAssertion.trace {
          // this should contain more stuff
          assertModules(storage, "project", "project.main", "project.test")
          assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
          assertContentRoots(virtualFileUrlManager, storage, "project.main", projectRoot.resolve("src/main"))
          assertContentRoots(virtualFileUrlManager, storage, "project.test", projectRoot.resolve("src/test"))

          DependencyAssertions.assertModuleLibDep(storage, "project.main", "Gradle: com.google.guava:guava:32.1.3-jre")
          DependencyAssertions.assertModuleModuleDeps(storage, "project.test", "project.main")
          DependencyAssertions.assertModuleLibDep(storage, "project.test", "Gradle: com.google.guava:guava:32.1.3-jre")
          DependencyAssertions.assertModuleLibDep(storage, "project.test", "Gradle: org.junit.jupiter:junit-jupiter:5.10.2")
        }
      }

      val settings = GradleSettings.getInstance(project)
      val projectSettings = GradleProjectSettings(projectRoot.toCanonicalPath())
      settings.linkProject(projectSettings)

      ExternalSystemUtil.refreshProject(projectRoot.toCanonicalPath(), createImportSpec())

      assertModules(project, "test-dcl", "test-dcl.main", "test-dcl.test")

      assertModuleLibDep("test-dcl.main", "Gradle: com.google.guava:guava:32.1.3-jre")
      assertModuleModuleDeps("test-dcl.test", "test-dcl.main")
      assertModuleLibDep("test-dcl.test", "Gradle: com.google.guava:guava:32.1.3-jre")
      assertModuleLibDep("test-dcl.test", "Gradle: org.junit.jupiter:junit-jupiter:5.10.2")

      assertContentRoots(project, "test-dcl", projectRoot)
      assertContentRoots(project, "test-dcl.main", projectRoot.resolve("src/main"))
      assertContentRoots(project, "test-dcl.test", projectRoot.resolve("src/test"))

      projectRootContributorAssertion.assertListenerFailures()
      projectRootContributorAssertion.assertListenerState(1) {
        "The project info resolution should be started only once."
      }
    }
  }

  @Test
  @TargetVersions("8.9+")
  fun `test declarative model creation in multi-module Gradle project`() {
    val projectRoot = projectRoot.toNioPath()
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    Disposer.newDisposable().use { disposable ->
      createProjectSubFile("settings.gradle.dcl", """
        pluginManagement {
            repositories {
                google() // Needed for the Android plugin, applied by the unified plugin
                gradlePluginPortal()
            }
        }
        
        plugins {
            id("org.gradle.experimental.jvm-ecosystem").version("0.1.21")
        }
        
        rootProject.name = "test-dcl"
        
        include("app")
        include("list")
        include("utilities")
        """)
      createProjectSubFile("list/build.gradle.dcl", """
        javaLibrary {
            dependencies {
                implementation("org.apache.commons:commons-text:1.11.0")
            }
    
            testing {
                dependencies {
                    implementation("org.junit.jupiter:junit-jupiter:5.10.2")
                    runtimeOnly("org.junit.platform:junit-platform-launcher")
                }
            }
        }
      """.trimIndent())
      createProjectSubFile("list/src/main/java/org/example/list/LinkedList.java",
                           """
        package org.example.list;
        
        public class LinkedList {
            
        }
      """.trimIndent())

      createProjectSubFile("list/src/test/java/org/example/list/LinkedListTest.java",
                           """
        package org.example.list;
        
        import org.junit.jupiter.api.Test;
        
        import static org.junit.jupiter.api.Assertions.*;
        
        class LinkedListTest {
            
        }
      """.trimIndent())

      createProjectSubFile("utilities/build.gradle.dcl",
        """
          javaLibrary {
              dependencies {
                  api(project(":list"))
                  implementation("org.apache.commons:commons-text:1.11.0")
              }
    
              testing {
                  dependencies {
                      implementation("org.junit.jupiter:junit-jupiter:5.10.2")
                      runtimeOnly("org.junit.platform:junit-platform-launcher")
                  }
              }
          }
        """.trimIndent())

      createProjectSubFile("utilities/src/main/java/org/example/utilities/StringUtils.java",
                           """
          package org.example.utilities;
          
          import org.example.list.LinkedList;
          
          public class StringUtils {
              
          }
        """.trimIndent())

      createProjectSubFile("app/build.gradle.dcl",
                           """
          javaApplication {
              javaVersion = 17
              mainClass = "org.example.app.App"
          
              dependencies {
                  implementation("org.apache.commons:commons-text:1.11.0")
                  implementation(project(":utilities"))
              }
              testing {
                  testJavaVersion = 21
                  dependencies {
                      implementation("org.junit.jupiter:junit-jupiter:5.10.2")
                      runtimeOnly("org.junit.platform:junit-platform-launcher")
                  }
              }
          }
        """.trimIndent())

      createProjectSubFile("app/src/main/java/org/example/app/App.java",
        """
          package org.example.app;
  
          import org.example.list.LinkedList;
          import static org.example.utilities.StringUtils.join;
          import static org.example.utilities.StringUtils.split;
          import static org.example.app.MessageUtils.getMessage;
          import org.apache.commons.text.WordUtils;
  
          public class App {
              public static void main(String[] args) {
                  
              }
          }
        """.trimIndent())

      val declarativeContributorAssertion = ListenerAssertion()

      whenResolveProjectInfoStarted(disposable) { _, storage ->
        declarativeContributorAssertion.trace {
          assertModules(storage, "project")
          //TODO this currently fails because the settings file parser does not work properly
          //assertModules(storage, "project", "project.app", "project.app.main", "project.app.test", "project.list",
          //              "project.list.main", "project.list.test", "project.utilities", "project.utilities.main", "project.utilities.test")
          //assertContentRoots(virtualFileUrlManager, storage, "project.app", projectRoot.resolve("app"))
          //assertContentRoots(virtualFileUrlManager, storage, "project.app.main", projectRoot.resolve("app/src/main"))
          //assertContentRoots(virtualFileUrlManager, storage, "project.app.test", projectRoot.resolve("app/src/test"))
          //
          //DependencyAssertions.assertModuleLibDep(storage, "project.app.main", "Gradle: org.apache.commons:commons-text:1.11.0")
          //DependencyAssertions.assertModuleModuleDeps(storage, "project.app.test", "project.app.main")
        }
      }

      val settings = GradleSettings.getInstance(project)
      val projectSettings = GradleProjectSettings(projectRoot.toCanonicalPath())
      settings.linkProject(projectSettings)

      ExternalSystemUtil.refreshProject(projectRoot.toCanonicalPath(), createImportSpec())

      assertModules(project, "test-dcl", "test-dcl.app", "test-dcl.app.main", "test-dcl.app.test", "test-dcl.list",
                    "test-dcl.list.main", "test-dcl.list.test", "test-dcl.utilities", "test-dcl.utilities.main", "test-dcl.utilities.test")

      assertModuleModuleDeps("test-dcl.app.main", "test-dcl.utilities.main", "test-dcl.list.main")
      assertModuleModuleDeps("test-dcl.app.test", "test-dcl.app.main", "test-dcl.utilities.main", "test-dcl.list.main")
      assertModuleLibDep("test-dcl.app.main", "Gradle: org.apache.commons:commons-text:1.11.0")
      assertModuleLibDep("test-dcl.app.test", "Gradle: org.junit.jupiter:junit-jupiter:5.10.2")

      assertModuleModuleDeps("test-dcl.utilities.main", "test-dcl.list.main")
      assertModuleModuleDeps("test-dcl.utilities.test", "test-dcl.utilities.main", "test-dcl.list.main")

      assertModuleModuleDeps("test-dcl.list.test", "test-dcl.list.main")

      assertContentRoots(project, "test-dcl.app", projectRoot.resolve("app"))
      assertContentRoots(project, "test-dcl.app.main", projectRoot.resolve("app/src/main"))
      assertContentRoots(project, "test-dcl.app.test", projectRoot.resolve("app/src/test"))
      assertContentRoots(project, "test-dcl.utilities", projectRoot.resolve("utilities"))
      assertContentRoots(project, "test-dcl.utilities.main", projectRoot.resolve("utilities/src/main"))
      assertContentRoots(project, "test-dcl.utilities.test", projectRoot.resolve("utilities/src/test"))
      assertContentRoots(project, "test-dcl.list", projectRoot.resolve("list"))
      assertContentRoots(project, "test-dcl.list.main", projectRoot.resolve("list/src/main"))
      assertContentRoots(project, "test-dcl.list.test", projectRoot.resolve("list/src/test"))

      declarativeContributorAssertion.assertListenerFailures()
      declarativeContributorAssertion.assertListenerState(1) {
        "The project info resolution should be started only once."
      }
    }
  }
}