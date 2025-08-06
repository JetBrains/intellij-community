// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.util.text.StringUtil
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptTreeBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptTreeBuilder.Companion.tree
import org.jetbrains.plugins.gradle.util.GradleEnvironment
import kotlin.apply as applyKt

@ApiStatus.Internal
abstract class AbstractGradleBuildScriptBuilder<Self : GradleBuildScriptBuilder<Self>>(
  gradleVersion: GradleVersion,
  gradleDsl: GradleDsl,
) : AbstractGradleBuildScriptBuilderCore<Self>(gradleVersion, gradleDsl),
    GradleBuildScriptBuilder<Self> {

  private val PREDEFINED_TASKS = setOf("test", "compileJava", "compileTestJava")

  protected val kotlinVersion: String = getKotlinVersion(gradleVersion)
  protected val groovyVersion: String = getGroovyVersion()
  protected val junit4Version: String = getJunit4Version()
  protected val junit5Version: String = getJunit5Version()

  override fun addGroup(group: String): Self =
    withPrefix { assign("group", group) }

  override fun addVersion(version: String): Self =
    withPrefix { assign("version", version) }

  override fun registerTask(name: String, type: String?, configure: GradleScriptTreeBuilder.() -> Unit): Self =
    withPostfix {
      val nameArgument = argument(name)
      val blockArgument = tree(configure).takeUnless { it.isEmpty() }?.let { argument(it) }
      when (gradleDsl) {
        GradleDsl.GROOVY -> if (isTaskConfigurationAvoidanceSupported(gradleVersion)) {
          val typeArgument = type?.let { argument(code(it)) }
          call("tasks.register", listOfNotNull(nameArgument, typeArgument, blockArgument))
        }
        else {
          val typeArgument = type?.let { argument(code(it)) }
          call("tasks.create", listOfNotNull(nameArgument, typeArgument, blockArgument))
        }
        GradleDsl.KOTLIN -> if (isTaskConfigurationAvoidanceSupported(gradleVersion)) {
          val typeArgument = type?.let { "<$it>" } ?: ""
          call("tasks.register$typeArgument", listOfNotNull(nameArgument, blockArgument))
        }
        else {
          val typeArgument = type?.let { argument(code("$it::class.java")) }
          call("tasks.create", listOfNotNull(nameArgument, typeArgument, blockArgument))
        }
      }
    }

  override fun configureTask(name: String, type: String, configure: GradleScriptTreeBuilder.() -> Unit): Self =
    withPostfix {
      val block = tree(configure)
      if (!block.isEmpty()) {
        when (gradleDsl) {
          GradleDsl.GROOVY -> when (name in PREDEFINED_TASKS) {
            true -> call(name, argument(block))
            else -> call("tasks.named", argument(name), argument(code(type)), argument(block))
          }
          GradleDsl.KOTLIN -> when (name in PREDEFINED_TASKS) {
            true -> call("tasks.$name", argument(block))
            else -> call("tasks.named<$type>", argument(name), argument(block))
          }
        }
      }
    }

  override fun configureTestTask(configure: GradleScriptTreeBuilder.() -> Unit): Self =
    configureTask("test", "Test", configure)

  override fun addDependency(scope: String, dependency: String, sourceSet: String?): Self =
    addDependency(scope, string(dependency), sourceSet)

  override fun addDependency(scope: String, dependency: Expression, sourceSet: String?): Self = apply {
    val dependencyScope = if (sourceSet == null) scope else sourceSet + StringUtil.capitalize(scope)
    withDependency { call(dependencyScope, dependency) }
  }

  override fun addApiDependency(dependency: String, sourceSet: String?): Self =
    addApiDependency(string(dependency), sourceSet)

  override fun addApiDependency(dependency: Expression, sourceSet: String?): Self =
    addDependency("api", dependency, sourceSet)

  override fun addCompileOnlyDependency(dependency: String, sourceSet: String?): Self =
    addCompileOnlyDependency(string(dependency), sourceSet)

  override fun addCompileOnlyDependency(dependency: Expression, sourceSet: String?): Self =
    addDependency("compileOnly", dependency, sourceSet)

  override fun addImplementationDependency(dependency: String, sourceSet: String?): Self =
    addImplementationDependency(string(dependency), sourceSet)

  override fun addImplementationDependency(dependency: Expression, sourceSet: String?): Self =
    addDependency("implementation", dependency, sourceSet)

  override fun addRuntimeOnlyDependency(dependency: String, sourceSet: String?): Self =
    addRuntimeOnlyDependency(string(dependency), sourceSet)

  override fun addRuntimeOnlyDependency(dependency: Expression, sourceSet: String?): Self =
    addDependency("runtimeOnly", dependency, sourceSet)

  override fun addTestImplementationDependency(dependency: String): Self =
    addImplementationDependency(dependency, sourceSet = "test")

  override fun addTestImplementationDependency(dependency: Expression): Self =
    addImplementationDependency(dependency, sourceSet = "test")

  override fun addTestRuntimeOnlyDependency(dependency: String): Self =
    addRuntimeOnlyDependency(dependency, sourceSet = "test")

  override fun addTestRuntimeOnlyDependency(dependency: Expression): Self =
    addRuntimeOnlyDependency(dependency, sourceSet = "test")

  override fun addBuildScriptClasspath(dependency: String): Self =
    addBuildScriptClasspath(string(dependency))

  override fun addBuildScriptClasspath(dependency: Expression): Self =
    withBuildScriptDependency { call("classpath", dependency) }

  override fun withBuildScriptMavenCentral(): Self =
    withBuildScriptRepository { mavenCentral() }

  override fun withMavenCentral(): Self =
    withRepository { mavenCentral() }

  override fun applyPlugin(plugin: String): Self =
    withPrefix {
      call("apply", "plugin" to plugin)
    }

  override fun applyPluginFrom(path: String): Self =
    withPrefix {
      call("apply", "from" to path)
    }

  override fun withPlugin(id: String, version: String?): Self =
    withPlugin {
      when (version) {
        null -> call("id", id)
        else -> infixCall(call("id", id), "version", string(version))
      }
    }

  override fun withJavaPlugin(): Self =
    withPlugin("java")

  override fun withJavaLibraryPlugin(): Self =
    withPlugin("java-library")

  override fun withIdeaPlugin(): Self =
    withPlugin("idea")

  override fun withKotlinJvmPlugin(): Self =
    withKotlinJvmPlugin(kotlinVersion)

  override fun withKotlinJvmPlugin(version: String?): Self = apply {
    withMavenCentral()
    when (gradleDsl) {
      GradleDsl.GROOVY -> withPlugin("org.jetbrains.kotlin.jvm", version)
      GradleDsl.KOTLIN -> withPlugin {
        when (version) {
          null -> call("kotlin", "jvm")
          else -> infixCall(call("kotlin", "jvm"), "version", string(version))
        }
      }
    }
  }

  override fun withKotlinJsPlugin(): Self =
    withPlugin("org.jetbrains.kotlin.js", kotlinVersion)

  override fun withKotlinMultiplatformPlugin(): Self =
    withPlugin("org.jetbrains.kotlin.multiplatform", kotlinVersion)

  override fun withKotlinJvmToolchain(jvmTarget: Int): Self =
    withKotlin {
      // We use a code here to force the generator to use parenthesis in Groovy, to be in-line with the documentation
      code("jvmToolchain($jvmTarget)")
    }

  override fun withKotlinDsl(): Self = apply {
    withMavenCentral()
    withPlugin {
      code("`kotlin-dsl`")
    }
  }

  override fun withGroovyPlugin(): Self =
    withGroovyPlugin(groovyVersion)

  override fun withGroovyPlugin(version: String): Self = apply {
    withPlugin("groovy")
    withMavenCentral()
    if (isGroovyApacheSupported(version)) {
      addImplementationDependency("org.apache.groovy:groovy:$version")
    }
    else {
      addImplementationDependency("org.codehaus.groovy:groovy-all:$version")
    }
  }

  override fun withApplicationPlugin(
    mainClass: String?,
    mainModule: String?,
    executableDir: String?,
    defaultJvmArgs: List<String>?,
  ): Self = apply {
    withPlugin("application")
    withPostfix {
      callIfNotEmpty("application") {
        assignIfNotNull("mainModule", mainModule)
        assignIfNotNull("mainClass", mainClass)
        assignIfNotNull("executableDir", executableDir)
        assignIfNotNull("applicationDefaultJvmArgs", defaultJvmArgs?.toTypedArray()?.let { list(*it) })
      }
    }
  }

  override fun withKotlinTest(): Self = apply {
    when (gradleDsl) {
      GradleDsl.GROOVY -> {
        withMavenCentral()
        // The kotlin-test dependency version is inherited from the Kotlin plugin
        addTestImplementationDependency("org.jetbrains.kotlin:kotlin-test")
        configureTestTask {
          call("useJUnitPlatform")
        }
      }
      GradleDsl.KOTLIN -> {
        withMavenCentral()
        addTestImplementationDependency(call("kotlin", "test"))
        configureTestTask {
          call("useJUnitPlatform")
        }
      }
    }
  }

  override fun withJUnit(): Self = apply {
    when (isJunit5Supported(gradleVersion)) {
      true -> withJUnit5()
      else -> withJUnit4()
    }
  }

  override fun withJUnit4(): Self = apply {
    withMavenCentral()
    addTestImplementationDependency("junit:junit:$junit4Version")
  }

  override fun withJUnit5(): Self = apply {
    assert(isJunit5Supported(gradleVersion))
    withMavenCentral()
    when (isPlatformDependencySupported(gradleVersion)) {
      true -> {
        addTestImplementationDependency(call("platform", "org.junit:junit-bom:$junit5Version"))
        addTestImplementationDependency("org.junit.jupiter:junit-jupiter")
        if (isExplicitTestFrameworkRuntimeDeclarationRequired(gradleVersion)) {
          addTestRuntimeOnlyDependency("org.junit.platform:junit-platform-launcher")
        }
      }
      else -> {
        addTestImplementationDependency("org.junit.jupiter:junit-jupiter-api:$junit5Version")
        addTestImplementationDependency("org.junit.jupiter:junit-jupiter-params:$junit5Version")
        addTestRuntimeOnlyDependency("org.junit.jupiter:junit-jupiter-engine:$junit5Version")
      }
    }
    configureTestTask {
      call("useJUnitPlatform")
    }
  }

  override fun targetCompatibility(level: String): Self = apply {
    if (GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.2")) {
      withPostfix {
        assign("targetCompatibility", level)
      }
    }
    else {
      withJava {
        assign("targetCompatibility", level)
      }
    }
  }

  override fun sourceCompatibility(level: String): Self = apply {
    if (GradleVersionUtil.isGradleOlderThan(gradleVersion, "8.2")) {
      withPostfix {
        assign("sourceCompatibility", level)
      }
    }
    else {
      withJava {
        assign("sourceCompatibility", level)
      }
    }
  }

  override fun project(name: String): Expression =
    call("project", name)

  override fun project(name: String, configuration: String): Expression =
    call("project", "path" to name, "configuration" to configuration)

  override fun GradleScriptTreeBuilder.mavenRepository(url: String): GradleScriptTreeBuilder = applyKt {
    when (gradleDsl) {
      GradleDsl.GROOVY -> {
        call("maven") {
          assign("url", url)
        }
      }
      GradleDsl.KOTLIN -> {
        call("maven", "url" to url)
      }
    }
  }

  override fun GradleScriptTreeBuilder.mavenCentral(): GradleScriptTreeBuilder = applyKt {
    val mavenRepositoryUrl = GradleEnvironment.Urls.getMavenRepositoryUrl()
    if (mavenRepositoryUrl != null) {
      // it is possible to configure the mavenCentral repository via closure only since Gradle 5.3
      if (GradleVersionUtil.isGradleOlderThan(gradleVersion, "5.3")) {
        mavenRepository(mavenRepositoryUrl)
      }
      else {
        call("mavenCentral") {
          assign("url", call("uri", mavenRepositoryUrl))
        }
      }
    }
    else {
      call("mavenCentral")
    }
  }

  override fun GradleScriptTreeBuilder.mavenLocal(url: String): GradleScriptTreeBuilder = applyKt {
    when (gradleDsl) {
      GradleDsl.GROOVY -> {
        call("mavenLocal") {
          assign("url", url)
        }
      }
      GradleDsl.KOTLIN -> {

        call("mavenLocal") {
          assign("url", call("uri", url))
        }
      }
    }
  }

  internal class Impl(
    gradleVersion: GradleVersion,
    gradleDsl: GradleDsl,
  ) : AbstractGradleBuildScriptBuilder<Impl>(gradleVersion, gradleDsl) {
    override fun apply(action: Impl.() -> Unit) = applyKt(action)
  }
}