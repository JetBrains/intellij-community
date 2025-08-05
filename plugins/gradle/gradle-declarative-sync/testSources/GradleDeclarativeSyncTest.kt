// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.declarativeSync

import com.android.tools.idea.gradle.feature.flags.DeclarativeStudioSupport
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.use
import com.intellij.platform.testFramework.assertion.listenerAssertion.ListenerAssertion
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import org.jetbrains.plugins.gradle.importing.syncAction.GradlePhasedSyncTestCase
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class GradleDeclarativeSyncTest : GradlePhasedSyncTestCase() {

  override fun setUp() {
    super.setUp()
    DeclarativeStudioSupport.override(true) // enable android gradle declarative flag
  }

  @Test
  @TargetVersions("8.9+")
  fun `test declarative model creation in a simple Gradle project`() {
    val projectRoot = myProjectRoot.toNioPath()

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

      whenSyncPhaseCompleted(GradleSyncPhase.DECLARATIVE_PHASE, disposable) {
        projectRootContributorAssertion.trace {
          // this should contain more stuff
          ModuleAssertions.assertModules(myProject, "project", "project.main", "project.test")
          ContentRootAssertions.assertContentRoots(myProject, "project", projectRoot)
          ContentRootAssertions.assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
          ContentRootAssertions.assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))

          ModuleAssertions.assertModuleEntity(myProject, "project.main") { module ->
            DependencyAssertions.assertLibraryDependency(module, "Gradle: com.google.guava:guava:32.1.3-jre")
          }
          ModuleAssertions.assertModuleEntity(myProject, "project.test") { module ->
            DependencyAssertions.assertModuleDependency(module, "project.main")
            DependencyAssertions.assertLibraryDependency(module, "Gradle: com.google.guava:guava:32.1.3-jre")
            DependencyAssertions.assertLibraryDependency(module, "Gradle: org.junit.jupiter:junit-jupiter:5.10.2")
          }
        }
      }

      val settings = GradleSettings.getInstance(myProject)
      val projectSettings = GradleProjectSettings(projectRoot.toCanonicalPath())
      settings.linkProject(projectSettings)

      ExternalSystemUtil.refreshProject(projectRoot.toCanonicalPath(), createImportSpec())

      ModuleAssertions.assertModules(myProject, "test-dcl", "test-dcl.main", "test-dcl.test")

      assertModuleLibDep("test-dcl.main", "Gradle: com.google.guava:guava:32.1.3-jre")
      assertModuleModuleDeps("test-dcl.test", "test-dcl.main")
      assertModuleLibDep("test-dcl.test", "Gradle: com.google.guava:guava:32.1.3-jre")
      assertModuleLibDep("test-dcl.test", "Gradle: org.junit.jupiter:junit-jupiter:5.10.2")

      ContentRootAssertions.assertContentRoots(myProject, "test-dcl", projectRoot)
      ContentRootAssertions.assertContentRoots(myProject, "test-dcl.main", projectRoot.resolve("src/main"))
      ContentRootAssertions.assertContentRoots(myProject, "test-dcl.test", projectRoot.resolve("src/test"))

      projectRootContributorAssertion.assertListenerFailures()
      projectRootContributorAssertion.assertListenerState(1) {
        "The project info resolution should be started only once."
      }
    }
  }

  //TODO @Test
  @TargetVersions("8.9+")
  fun `test declarative model creation in multi-module Gradle project`() {
    val projectRoot = myProjectRoot.toNioPath()

    Disposer.newDisposable().use { disposable ->
      createProjectSubFile("settings.gradle.dcl", """
        pluginManagement {
            repositories {
                google() // Needed for the Android plugin, applied by the unified plugin
                gradlePluginPortal()
            }
        }
        
        plugins {
            //id("org.gradle.experimental.jvm-ecosystem").version("0.1.21")
        }
        
        //rootProject.name = "test-dcl"
        
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

      whenSyncPhaseCompleted(GradleSyncPhase.DECLARATIVE_PHASE, disposable) {
        declarativeContributorAssertion.trace {
          ModuleAssertions.assertModules(myProject, "project", "project.app", "project.app.main", "project.app.test", "project.list",
                        "project.list.main", "project.list.test", "project.utilities", "project.utilities.main", "project.utilities.test")
          ContentRootAssertions.assertContentRoots(myProject, "project.app", projectRoot.resolve("app"))
          ContentRootAssertions.assertContentRoots(myProject, "project.app.main", projectRoot.resolve("app/src/main"))
          ContentRootAssertions.assertContentRoots(myProject, "project.app.test", projectRoot.resolve("app/src/test"))
          ContentRootAssertions.assertContentRoots(myProject, "project.utilities", projectRoot.resolve("utilities"))
          ContentRootAssertions.assertContentRoots(myProject, "project.utilities.main", projectRoot.resolve("utilities/src/main"))
          ContentRootAssertions.assertContentRoots(myProject, "project.utilities.test", projectRoot.resolve("utilities/src/test"))
          ContentRootAssertions.assertContentRoots(myProject, "project.list", projectRoot.resolve("list"))
          ContentRootAssertions.assertContentRoots(myProject, "project.list.main", projectRoot.resolve("list/src/main"))
          ContentRootAssertions.assertContentRoots(myProject, "project.list.test", projectRoot.resolve("list/src/test"))

          ModuleAssertions.assertModuleEntity(myProject, "project.app.main") { module ->
            DependencyAssertions.assertLibraryDependency(module, "Gradle: org.apache.commons:commons-text:1.11.0")
          }

          // TODO add transitive module dependencies
          //  (if app depends on utilities and utilities depends on list then app should also depend on list)
          //DependencyAssertions.assertModuleModuleDeps(storage,
          // "project.app.main", "project.utilities.main", "project.list.main")
          //DependencyAssertions.assertModuleModuleDeps(myProject, "project.app.test",
          // "project.app.main", "project.utilities.main", "project.list.main")

          ModuleAssertions.assertModuleEntity(myProject, "project.app.main") { module ->
            DependencyAssertions.assertLibraryDependency(module, "Gradle: org.apache.commons:commons-text:1.11.0")
          }
          ModuleAssertions.assertModuleEntity(myProject, "project.app.test") { module ->
            DependencyAssertions.assertLibraryDependency(module, "Gradle: org.junit.jupiter:junit-jupiter:5.10.2")
          }

          ModuleAssertions.assertModuleEntity(myProject, "project.utilities.main") { module ->
            DependencyAssertions.assertModuleDependencies(module, "project.list.main")
          }
          ModuleAssertions.assertModuleEntity(myProject, "project.utilities.test") { module ->
            DependencyAssertions.assertModuleDependencies(module, "project.utilities.main", "project.list.main")
          }

          ModuleAssertions.assertModuleEntity(myProject, "project.list.test") { module ->
            DependencyAssertions.assertModuleDependency(module, "project.list.main")
          }
        }
      }

      val settings = GradleSettings.getInstance(myProject)
      val projectSettings = GradleProjectSettings(projectRoot.toCanonicalPath())
      settings.linkProject(projectSettings)

      ExternalSystemUtil.refreshProject(projectRoot.toCanonicalPath(), createImportSpec())

      //TODO this currently fails because the settings file parser does not work properly
      //ModuleAssertions.assertModules(myProject, "test-dcl", "test-dcl.app", "test-dcl.app.main", "test-dcl.app.test", "test-dcl.list",
      //                               "test-dcl.list.main", "test-dcl.list.test", "test-dcl.utilities", "test-dcl.utilities.main",
      //                               "test-dcl.utilities.test")
      //
      //assertModuleModuleDeps("test-dcl.app.main", "test-dcl.utilities.main", "test-dcl.list.main")
      //assertModuleModuleDeps("test-dcl.app.test", "test-dcl.app.main", "test-dcl.utilities.main", "test-dcl.list.main")
      //assertModuleLibDep("test-dcl.app.main", "Gradle: org.apache.commons:commons-text:1.11.0")
      //assertModuleLibDep("test-dcl.app.test", "Gradle: org.junit.jupiter:junit-jupiter:5.10.2")
      //
      //assertModuleModuleDeps("test-dcl.utilities.main", "test-dcl.list.main")
      //assertModuleModuleDeps("test-dcl.utilities.test", "test-dcl.utilities.main", "test-dcl.list.main")
      //
      //assertModuleModuleDeps("test-dcl.list.test", "test-dcl.list.main")
      //
      //ContentRootAssertions.assertContentRoots(myProject, "test-dcl.app", projectRoot.resolve("app"))
      //ContentRootAssertions.assertContentRoots(myProject, "test-dcl.app.main", projectRoot.resolve("app/src/main"))
      //ContentRootAssertions.assertContentRoots(myProject, "test-dcl.app.test", projectRoot.resolve("app/src/test"))
      //ContentRootAssertions.assertContentRoots(myProject, "test-dcl.utilities", projectRoot.resolve("utilities"))
      //ContentRootAssertions.assertContentRoots(myProject, "test-dcl.utilities.main", projectRoot.resolve("utilities/src/main"))
      //ContentRootAssertions.assertContentRoots(myProject, "test-dcl.utilities.test", projectRoot.resolve("utilities/src/test"))
      //ContentRootAssertions.assertContentRoots(myProject, "test-dcl.list", projectRoot.resolve("list"))
      //ContentRootAssertions.assertContentRoots(myProject, "test-dcl.list.main", projectRoot.resolve("list/src/main"))
      //ContentRootAssertions.assertContentRoots(myProject, "test-dcl.list.test", projectRoot.resolve("list/src/test"))

      declarativeContributorAssertion.assertListenerFailures()
      declarativeContributorAssertion.assertListenerState(1) {
        "The project info resolution should be started only once."
      }
    }
  }
}