// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.getJunit4Version
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.getJunit5Version
import org.junit.jupiter.api.Test

class GradleBuildScriptBuilderTest : GradleBuildScriptBuilderTestCase() {

  @Test
  fun `test empty build script`() {
    assertBuildScript(GradleVersion.current() to ("" to "")) {}
  }

  @Test
  fun `test build script with plugins block`() {
    assertBuildScript("""
      import org.example.Class1
      import org.example.Class2
      
      plugins {
          id 'plugin-id'
      }
      
      println 'Hello, Prefix!'
      
      repositories {
          repositoryCentral()
      }
      
      dependencies {
          dependency 'my-dependency-id'
      }
      
      println hello(postfix)
    """.trimIndent(), """
      import org.example.Class1
      import org.example.Class2
      
      plugins {
          id("plugin-id")
      }
      
      println("Hello, Prefix!")
      
      repositories {
          repositoryCentral()
      }
      
      dependencies {
          dependency("my-dependency-id")
      }
      
      println(hello(postfix))
    """.trimIndent()) {
      addImport("org.example.Class1")
      addImport("org.example.Class2")
      withPlugin("plugin-id")
      addRepository("repositoryCentral()")
      addDependency("dependency", "my-dependency-id")
      withPrefix { call("println", "Hello, Prefix!") }
      withPostfix { call("println", call("hello", code("postfix"))) }
    }
  }

  @Test
  fun `test build script with buildscript block`() {
    assertBuildScript("""
      import org.classpath.Build
      
      buildscript {
          println 'Hello, Prefix!'
      
          repositories {
              repo file('build/repo')
          }
      
          dependencies {
              classpath file('build/targets/org/classpath/archive.jar')
          }
      
          println 'Hello, Postfix!'
      }
      
      apply plugin: 'gradle-build'
      Build.configureSuperGradleBuild {
          makeBeautiful()
      }
    """.trimIndent(), """
      import org.classpath.Build
      
      buildscript {
          println("Hello, Prefix!")
      
          repositories {
              repo(file("build/repo"))
          }
      
          dependencies {
              classpath(file("build/targets/org/classpath/archive.jar"))
          }
      
          println("Hello, Postfix!")
      }
      
      apply(plugin = "gradle-build")
      Build.configureSuperGradleBuild {
          makeBeautiful()
      }
    """.trimIndent()) {
      withBuildScriptPrefix { call("println", "Hello, Prefix!") }
      withBuildScriptRepository { call("repo", call("file", "build/repo")) }
      withBuildScriptDependency { call("classpath", call("file", "build/targets/org/classpath/archive.jar")) }
      withBuildScriptPostfix { call("println", "Hello, Postfix!") }
      applyPlugin("gradle-build")
      addImport("org.classpath.Build")
      withPrefix {
        call("Build.configureSuperGradleBuild") {
          call("makeBeautiful")
        }
      }
    }
  }

  @Test
  fun `test build script deduplication`() {
    assertBuildScript("""
      plugins {
          id 'java'
      }
      
      repositories {
          mavenCentral()
      }
      
      block('name') {
          configure()
      }
    """.trimIndent(), """
      plugins {
          id("java")
      }
      
      repositories {
          mavenCentral()
      }
      
      block("name") {
          configure()
      }
    """.trimIndent()) {
      withJavaPlugin()
      withJavaPlugin()
      withMavenCentral()
      withMavenCentral()
      withPostfix {
        call("block", "name") {
          call("configure")
        }
      }
      withPostfix {
        call("block", "name") {
          call("configure")
        }
      }
    }
  }

  @Test
  fun `test compile-implementation dependency scope`() {
    assertBuildScript(
      GradleVersion.current() to ("""
        dependencies {
            implementation 'my-dep'
            runtimeOnly 'my-runtime-dep'
            testImplementation 'my-test-dep'
            testRuntimeOnly 'my-runtime-dep'
        }
      """.trimIndent() to """
        dependencies {
            implementation("my-dep")
            runtimeOnly("my-runtime-dep")
            testImplementation("my-test-dep")
            testRuntimeOnly("my-runtime-dep")
        }
      """.trimIndent()),

      GradleVersion.version("3.0") to ("""
        dependencies {
            compile 'my-dep'
            runtime 'my-runtime-dep'
            testCompile 'my-test-dep'
            testRuntime 'my-runtime-dep'
        }
      """.trimIndent() to """
        dependencies {
            compile("my-dep")
            runtime("my-runtime-dep")
            testCompile("my-test-dep")
            testRuntime("my-runtime-dep")
        }
      """.trimIndent())
    ) {
      addImplementationDependency("my-dep")
      addRuntimeOnlyDependency("my-runtime-dep")
      addTestImplementationDependency("my-test-dep")
      addTestRuntimeOnlyDependency("my-runtime-dep")
    }
  }

  @Test
  fun `test application plugin building`() {
    assertBuildScript("""
      plugins {
          id 'application'
      }
    """.trimIndent(), """
      plugins {
          id("application")
      }
    """.trimIndent()) {
      withApplicationPlugin()
    }
    assertBuildScript("""
      plugins {
          id 'application'
      }
      
      application {
          mainClass = 'MyMain'
      }
    """.trimIndent(), """
      plugins {
          id("application")
      }
      
      application {
          mainClass = "MyMain"
      }
    """.trimIndent()) {
      withApplicationPlugin("MyMain")
    }
    assertBuildScript("""
      plugins {
          id 'application'
      }
      
      application {
          mainModule = 'org.gradle.sample.app'
          mainClass = 'org.gradle.sample.Main'
          executableDir = 'custom_bin_dir'
          applicationDefaultJvmArgs = ['-Dgreeting.language=en']
      }
    """.trimIndent(), """
      plugins {
          id("application")
      }
      
      application {
          mainModule = "org.gradle.sample.app"
          mainClass = "org.gradle.sample.Main"
          executableDir = "custom_bin_dir"
          applicationDefaultJvmArgs = listOf("-Dgreeting.language=en")
      }
    """.trimIndent()) {
      withApplicationPlugin(
        mainClass = "org.gradle.sample.Main",
        mainModule = "org.gradle.sample.app",
        executableDir = "custom_bin_dir",
        defaultJvmArgs = listOf("-Dgreeting.language=en"))
    }
  }

  @Test
  fun `test junit dependency generation`() {
    val junit4 = getJunit4Version()
    val junit5 = getJunit5Version()

    assertBuildScript(
      GradleVersion.current() to ("""
        repositories {
            mavenCentral()
        }
        
        dependencies {
            testImplementation platform('org.junit:junit-bom:$junit5')
            testImplementation 'org.junit.jupiter:junit-jupiter'
        }
        
        test {
            useJUnitPlatform()
        }
      """.trimIndent() to """
        repositories {
            mavenCentral()
        }
        
        dependencies {
            testImplementation(platform("org.junit:junit-bom:$junit5"))
            testImplementation("org.junit.jupiter:junit-jupiter")
        }
        
        tasks.test {
            useJUnitPlatform()
        }
      """.trimIndent()),

      GradleVersion.version("4.9") to ("""
        repositories {
            mavenCentral()
        }
        
        dependencies {
            testImplementation 'org.junit.jupiter:junit-jupiter-api:$junit5'
            testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:$junit5'
        }
        
        test {
            useJUnitPlatform()
        }
      """.trimIndent() to """
        repositories {
            mavenCentral()
        }
        
        dependencies {
            testImplementation("org.junit.jupiter:junit-jupiter-api:$junit5")
            testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit5")
        }
        
        tasks.test {
            useJUnitPlatform()
        }
      """.trimIndent()),

      GradleVersion.version("4.6") to ("""
        repositories {
            mavenCentral()
        }
        
        dependencies {
            testImplementation 'junit:junit:$junit4'
        }
      """.trimIndent() to """
        repositories {
            mavenCentral()
        }
        
        dependencies {
            testImplementation("junit:junit:$junit4")
        }
      """.trimIndent())
    ) {
      withJUnit()
    }
  }
}