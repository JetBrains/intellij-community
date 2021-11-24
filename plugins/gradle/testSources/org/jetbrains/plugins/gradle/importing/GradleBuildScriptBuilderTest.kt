// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GroovyDslGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.KotlinDslGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.getJunit4Version
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.getJunit5Version
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder.Companion.buildscript
import org.junit.Test

class GradleBuildScriptBuilderTest {
  @Test
  fun `test empty build script`() {
    assertThat(buildscript(GradleVersion.current()) {})
      .isEqualTo("")
  }

  @Test
  fun `test build script with plugins block`() {
    assertThat(buildscript(GradleVersion.current()) {
      addImport("org.example.Class1")
      addImport("org.example.Class2")
      withPlugin("plugin-id")
      addRepository("repositoryCentral()")
      addDependency("dependency", "my-dependency-id")
      withPrefix { call("println", "Hello, Prefix!") }
      withPostfix { call("println", call("hello", code("postfix"))) }
    }).isEqualTo("""
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
    """.trimIndent())
  }

  @Test
  fun `test build script with buildscript block`() {
    assertThat(buildscript(GradleVersion.current()) {
      addBuildScriptPrefix("println 'Hello, Prefix!'")
      withBuildScriptRepository { call("repo", code("file('build/repo')")) }
      withBuildScriptDependency { call("classpath", code("file('build/targets/org/classpath/archive.jar')")) }
      addBuildScriptPostfix("println 'Hello, Postfix!'")
      applyPlugin("'gradle-build'")
      addImport("org.classpath.Build")
      withPrefix {
        call("Build.configureSuperGradleBuild") {
          call("makeBeautiful")
        }
      }
    }).isEqualTo("""
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
    """.trimIndent())
  }

  @Test
  fun `test build script deduplication`() {
    val junit5 = getJunit5Version()
    assertThat(buildscript(GradleVersion.current()) {
      withJUnit()
      withJUnit()
      withGroovyPlugin()
      withGroovyPlugin()
    }).isEqualTo("""
      plugins {
          id 'groovy'
      }
      
      repositories {
          maven {
              url 'https://repo.labs.intellij.net/repo1'
          }
      }
      
      dependencies {
          testImplementation 'org.junit.jupiter:junit-jupiter-api:$junit5'
          testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:$junit5'
          implementation 'org.codehaus.groovy:groovy-all:3.0.5'
      }
      
      test {
          useJUnitPlatform()
      }
    """.trimIndent())
  }

  @Test
  fun `test compile-implementation dependency scope`() {
    val junit4 = getJunit4Version()
    val junit5 = getJunit5Version()
    val configureScript: TestGradleBuildScriptBuilder.() -> Unit = {
      withJUnit()
      addImplementationDependency("my-dep")
      addRuntimeOnlyDependency(code("my-runtime-dep"))
    }
    assertThat(buildscript(GradleVersion.current(), configureScript))
      .isEqualTo("""
        repositories {
            maven {
                url 'https://repo.labs.intellij.net/repo1'
            }
        }
        
        dependencies {
            testImplementation 'org.junit.jupiter:junit-jupiter-api:$junit5'
            testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:$junit5'
            implementation 'my-dep'
            runtimeOnly my-runtime-dep
        }
        
        test {
            useJUnitPlatform()
        }
      """.trimIndent())
    assertThat(buildscript(GradleVersion.version("3.0"), configureScript))
      .isEqualTo("""
        repositories {
            maven {
                url 'https://repo.labs.intellij.net/repo1'
            }
        }
        
        dependencies {
            testCompile 'junit:junit:$junit4'
            compile 'my-dep'
            runtime my-runtime-dep
        }
      """.trimIndent())
    assertThat(buildscript(GradleVersion.version("4.0"), configureScript))
      .isEqualTo("""
        repositories {
            maven {
                url 'https://repo.labs.intellij.net/repo1'
            }
        }
        
        dependencies {
            testImplementation 'junit:junit:$junit4'
            implementation 'my-dep'
            runtimeOnly my-runtime-dep
        }
      """.trimIndent())
  }

  @Test
  fun `test application plugin building`() {
    assertThat(buildscript(GradleVersion.current()) {
      withApplicationPlugin()
    }).isEqualTo("""
        plugins {
            id 'application'
        }
      """.trimIndent())
    assertThat(buildscript(GradleVersion.current()) {
      withApplicationPlugin("MyMain")
    }).isEqualTo("""
        plugins {
            id 'application'
        }
        
        application {
            mainClass = 'MyMain'
        }
      """.trimIndent())
    assertThat(buildscript(GradleVersion.current()) {
      withApplicationPlugin(
        mainClass = "org.gradle.sample.Main",
        mainModule = "org.gradle.sample.app",
        executableDir = "custom_bin_dir",
        defaultJvmArgs = listOf("-Dgreeting.language=en"))
    }).isEqualTo("""
        plugins {
            id 'application'
        }
        
        application {
            mainModule = 'org.gradle.sample.app'
            mainClass = 'org.gradle.sample.Main'
            executableDir = 'custom_bin_dir'
            applicationDefaultJvmArgs = ['-Dgreeting.language=en']
        }
      """.trimIndent())
  }

  @Test
  fun `test child build script build`() {
    val junit4 = getJunit4Version()
    val junit5 = getJunit5Version()
    assertThat(buildscript(GradleVersion.current()) {
      withJUnit4()
      allprojects {
        withJavaPlugin()
        withJUnit5()
      }
    }).isEqualTo("""
        allprojects {
            apply plugin: 'java'
        
            repositories {
                maven {
                    url 'https://repo.labs.intellij.net/repo1'
                }
            }
        
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter-api:$junit5'
                testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:$junit5'
            }
        
            test {
                useJUnitPlatform()
            }
        }
        
        repositories {
            maven {
                url 'https://repo.labs.intellij.net/repo1'
            }
        }
        
        dependencies {
            testImplementation 'junit:junit:$junit4'
        }
      """.trimIndent())
  }

  @Test
  fun `test kotlin-groovy dsl generation`() {
    val junit5 = getJunit5Version()
    assertThat(
      GroovyDslGradleBuildScriptBuilder.create(GradleVersion.current())
        .withJUnit5()
        .generate()
    ).isEqualTo("""
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
    """.trimIndent())
    assertThat(
      KotlinDslGradleBuildScriptBuilder(GradleVersion.current())
        .withJUnit5()
        .generate()
    ).isEqualTo("""
        repositories {
            mavenCentral()
        }
        
        dependencies {
            testImplementation("org.junit.jupiter:junit-jupiter-api:$junit5")
            testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit5")
        }
        
        tasks.getByName<Test>("test") {
            useJUnitPlatform()
        }
    """.trimIndent())
  }
}