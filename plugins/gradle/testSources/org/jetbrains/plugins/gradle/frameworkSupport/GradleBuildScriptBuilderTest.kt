// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import com.intellij.testFramework.junit5.SystemProperty
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.getJunit4Version
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.getJunit5Version
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@SystemProperty("idea.gradle.mavenRepositoryUrl", "")
class GradleBuildScriptBuilderTest : GradleBuildScriptBuilderTestCase() {

  @Test
  fun `test empty build script`() {
    assertBuildScript("", "") {}
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
      addBuildScriptClasspath(call("file", "build/targets/org/classpath/archive.jar"))
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
    assertBuildScript("""
      dependencies {
          implementation 'my-dep'
          runtimeOnly 'my-runtime-dep'
          testImplementation 'my-test-dep'
          testRuntimeOnly 'my-runtime-dep'
      }
    """.trimIndent(), """
      dependencies {
          implementation("my-dep")
          runtimeOnly("my-runtime-dep")
          testImplementation("my-test-dep")
          testRuntimeOnly("my-runtime-dep")
      }
    """.trimIndent()) {
      addImplementationDependency("my-dep")
      addRuntimeOnlyDependency("my-runtime-dep")
      addTestImplementationDependency("my-test-dep")
      addTestRuntimeOnlyDependency("my-runtime-dep")
    }
  }

  @Nested
  inner class ApplicationPlugin {

    @Test
    fun `test apply`() {
      assertBuildScript("""
          |plugins {
          |    id 'application'
          |}
        """.trimMargin(), """
          |plugins {
          |    id("application")
          |}
        """.trimMargin()
      ) {
        withApplicationPlugin()
      }
    }

    @Test
    fun `test main class configuration`() {
      assertBuildScript(
        GradleVersion.version("4.10") to ("""
          |plugins {
          |    id 'application'
          |}
          |
          |application {
          |    mainClass = 'MyMain'
          |}
        """.trimMargin() to """
          |plugins {
          |    id("application")
          |}
          |
          |application {
          |    mainClass = "MyMain"
          |}
        """.trimMargin())
      ) {
        withApplicationPlugin("MyMain")
      }
    }

    @Test
    fun `test all properties configuration`() {
      assertBuildScript(
        GradleVersion.version("4.10") to ("""
          |plugins {
          |    id 'application'
          |}
          |
          |application {
          |    mainModule = 'org.gradle.sample.app'
          |    mainClass = 'org.gradle.sample.Main'
          |    executableDir = 'custom_bin_dir'
          |    applicationDefaultJvmArgs = ['-Dgreeting.language=en']
          |}
        """.trimMargin() to """
          |plugins {
          |    id("application")
          |}
          |
          |application {
          |    mainModule = "org.gradle.sample.app"
          |    mainClass = "org.gradle.sample.Main"
          |    executableDir = "custom_bin_dir"
          |    applicationDefaultJvmArgs = listOf("-Dgreeting.language=en")
          |}
        """.trimMargin())
      ) {
        withApplicationPlugin(
          mainClass = "org.gradle.sample.Main",
          mainModule = "org.gradle.sample.app",
          executableDir = "custom_bin_dir",
          defaultJvmArgs = listOf("-Dgreeting.language=en"))
      }
    }
  }

  @Test
  fun `test junit dependency generation`() {
    val junit4 = getJunit4Version()
    val junit5 = getJunit5Version()

    assertBuildScript(
      GradleVersion.version("8.2") to ("""
        repositories {
            mavenCentral()
        }
        
        dependencies {
            testImplementation platform('org.junit:junit-bom:$junit5')
            testImplementation 'org.junit.jupiter:junit-jupiter'
            testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
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
            testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        }
        
        tasks.test {
            useJUnitPlatform()
        }
      """.trimIndent()),

      GradleVersion.version("5.0") to ("""
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
            testImplementation 'org.junit.jupiter:junit-jupiter-params:$junit5'
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
            testImplementation("org.junit.jupiter:junit-jupiter-params:$junit5")
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

  @Test
  fun `test string escaping`() {
    assertBuildScript("""
      |def string = 'simple string'
      |println "string with ${'$'}string interpolation"
      |println '/example/unix/path'
      |println 'C:\\example\\win\\path'
      |println 'string with \' quote'
      |println 'string with " quote'
      |println 'multi-line\n joined\n string'
      |println 'multi-line\n raw\n string'
    """.trimMargin(), """
      |var string = "simple string"
      |println("string with ${'$'}string interpolation")
      |println("/example/unix/path")
      |println("C:\\example\\win\\path")
      |println("string with ' quote")
      |println("string with \" quote")
      |println("multi-line\n joined\n string")
      |println("multi-line\n raw\n string")
    """.trimMargin()) {
      withPostfix {
        property("string", "simple string")
        call("println", "string with ${'$'}string interpolation")
        call("println", "/example/unix/path")
        call("println", "C:\\example\\win\\path")
        call("println", "string with ' quote")
        call("println", "string with \" quote")
        call("println", "multi-line\n joined\n string")
        call("println", """
          |multi-line
          | raw
          | string
        """.trimMargin())
      }
    }
  }

  @Nested
  inner class TaskRegistration {

    @Test
    fun `test task registration without configuration`() {
      assertBuildScript(
        GradleVersion.version("4.9") to ("""
          |tasks.register 'myTask'
        """.trimMargin() to """
          |tasks.register("myTask")
        """.trimMargin()),

        GradleVersion.version("4.6") to ("""
          |tasks.create 'myTask'
        """.trimMargin() to """
          |tasks.create("myTask")
        """.trimMargin())
      ) {
        registerTask("myTask")
      }
    }

    @Test
    fun `test task registration with empty configuration block`() {
      assertBuildScript(
        GradleVersion.version("4.9") to ("""
          |tasks.register 'myTask', MyTask
        """.trimMargin() to """
          |tasks.register<MyTask>("myTask")
        """.trimMargin()),

        GradleVersion.version("4.6") to ("""
          |tasks.create 'myTask', MyTask
        """.trimMargin() to """
          |tasks.create("myTask", MyTask::class.java)
        """.trimMargin())
      ) {
        registerTask("myTask", "MyTask") {
          // no configuration
        }
      }
    }

    @Test
    fun `test simple task registration`() {
      assertBuildScript(
        GradleVersion.version("4.9") to ("""
          |tasks.register('myTask', MyTask) {
          |    myConfiguration()
          |}
        """.trimMargin() to """
          |tasks.register<MyTask>("myTask") {
          |    myConfiguration()
          |}
        """.trimMargin()),

        GradleVersion.version("4.6") to ("""
          |tasks.create('myTask', MyTask) {
          |    myConfiguration()
          |}
        """.trimMargin() to """
          |tasks.create("myTask", MyTask::class.java) {
          |    myConfiguration()
          |}
        """.trimMargin())
      ) {
        registerTask("myTask", "MyTask") {
          call("myConfiguration")
        }
      }
    }
  }

  @Nested
  inner class TaskConfiguration {

    @Test
    fun `test predefined task configuration`() {
      assertBuildScript("""
        |test {
        |    myConfiguration()
        |}
      """.trimMargin(), """
        |tasks.test {
        |    myConfiguration()
        |}
      """.trimMargin()) {
        configureTask("test", "Test") {
          call("myConfiguration")
        }
      }
    }

    @Test
    fun `test simple task configuration`() {
      assertBuildScript("""
        |tasks.named('myTask', MyTask) {
        |    myConfiguration()
        |}
      """.trimMargin(), """
        |tasks.named<MyTask>("myTask") {
        |    myConfiguration()
        |}
      """.trimMargin()) {
        configureTask("myTask", "MyTask") {
          call("myConfiguration")
        }
      }
    }

    @Test
    fun `test task configuration with empty configuration block`() {
      assertBuildScript("", "") {
        configureTask("myTask", "MyTask") {
          // no configuration
        }
      }
    }
  }

  @Nested
  inner class PluginExtension {

    @Test
    fun `test simple plugin extension`() {
      assertBuildScript(
        GradleVersion.version("4.10") to("""
          |extension {
          |    myConfiguration()
          |}
        """.trimMargin() to """
          |extension {
          |    myConfiguration()
          |}
        """.trimMargin())
      ) {
        withExtension("extension") {
          call("myConfiguration")
        }
      }
    }

    @Test
    fun `test empty plugin extension`() {
      assertBuildScript("", "") {
        withExtension("extension") {
          // no configuration
        }
      }
    }

    @Test
    fun `test line breaks between plugin extensions`() {
      assertBuildScript(
        GradleVersion.version("4.10") to("""
          |group = 'testing'
          |version = '1.0'
          |
          |configuration1 {
          |    myConfiguration()
          |}
          |
          |configuration2 {
          |    myConfiguration()
          |}
        """.trimMargin() to """
          |group = "testing"
          |version = "1.0"
          |
          |configuration1 {
          |    myConfiguration()
          |}
          |
          |configuration2 {
          |    myConfiguration()
          |}
        """.trimMargin())
      ) {
        addGroup("testing")
        addVersion("1.0")
        withExtension("configuration1") {
          call("myConfiguration")
        }
        withExtension("configuration2") {
          call("myConfiguration")
        }
      }
    }

    @Test
    fun `test deduplication inside plugin extension`() {
      assertBuildScript(
        GradleVersion.version("4.10") to("""
          |extension {
          |    myConfiguration()
          |}
        """.trimMargin() to """
          |extension {
          |    myConfiguration()
          |}
        """.trimMargin())
      ) {
        repeat(10) {
          withExtension("extension") {
            call("myConfiguration")
          }
        }
      }
    }

    @Test
    fun `test java, kotlin, application and custom plugin extensions`() {
      assertBuildScript(
        GradleVersion.version("4.10") to ("""
          |java {
          |    myConfiguration()
          |}
          |
          |kotlin {
          |    myConfiguration()
          |}
          |
          |application {
          |    myConfiguration()
          |}
          |
          |custom {
          |    myConfiguration()
          |}
        """.trimMargin() to """
          |java {
          |    myConfiguration()
          |}
          |
          |kotlin {
          |    myConfiguration()
          |}
          |
          |application {
          |    myConfiguration()
          |}
          |
          |custom {
          |    myConfiguration()
          |}
        """.trimMargin())
      ) {
        withJava {
          call("myConfiguration")
        }
        withKotlin {
          call("myConfiguration")
        }
        withApplication {
          call("myConfiguration")
        }
        withExtension("custom") {
          call("myConfiguration")
        }
      }
    }
  }

  @Nested
  inner class Toolchain {

    @Test
    fun `test Kotlin JVM toolchain`() {
      assertBuildScript(
        GradleVersion.version("4.10") to ("""
          |kotlin {
          |    jvmToolchain(17)
          |}
        """.trimMargin() to """
          |kotlin {
          |    jvmToolchain(17)
          |}
        """.trimMargin())
      ) {
        withKotlinJvmToolchain(17)
      }
    }

    @Test
    fun `test Java toolchain`() {
      assertBuildScript(
        GradleVersion.version("8.6") to ("""
          |java {
          |    toolchain {
          |        languageVersion = JavaLanguageVersion.of(17)
          |    }
          |}
        """.trimMargin() to """
          |java {
          |    toolchain {
          |        languageVersion = JavaLanguageVersion.of(17)
          |    }
          |}
        """.trimMargin()),

        GradleVersion.version("6.7") to ("""
          |java {
          |    toolchain {
          |        languageVersion = JavaLanguageVersion.of(17)
          |    }
          |}
        """.trimMargin() to """
          |java {
          |    toolchain {
          |        languageVersion.set(JavaLanguageVersion.of(17))
          |    }
          |}
        """.trimMargin())
      ) {
        withJavaToolchain(17)
      }
    }
  }
}