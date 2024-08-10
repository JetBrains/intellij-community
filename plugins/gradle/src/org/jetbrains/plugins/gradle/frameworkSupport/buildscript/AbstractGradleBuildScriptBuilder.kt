// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.util.text.StringUtil
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder
import org.jetbrains.plugins.gradle.util.GradleEnvironment
import kotlin.apply as applyKt

@ApiStatus.NonExtendable
@Suppress("MemberVisibilityCanBePrivate")
abstract class AbstractGradleBuildScriptBuilder<BSB : GradleBuildScriptBuilder<BSB>>(
  gradleVersion: GradleVersion
) : AbstractGradleBuildScriptBuilderCore<BSB>(gradleVersion), GradleBuildScriptBuilder<BSB> {

  protected val kotlinVersion = getKotlinVersion(gradleVersion)
  protected val groovyVersion = getGroovyVersion()
  protected val junit4Version = getJunit4Version()
  protected val junit5Version = getJunit5Version()

  override fun addGroup(group: String) =
    withPrefix { assign("group", group) }

  override fun addVersion(version: String) =
    withPrefix { assign("version", version) }

  override fun addDependency(scope: String, dependency: String, sourceSet: String?) =
    addDependency(scope, string(dependency), sourceSet)

  override fun addDependency(scope: String, dependency: Expression, sourceSet: String?) = apply {
    val dependencyScope = if (sourceSet == null) scope else sourceSet + StringUtil.capitalize(scope)
    withDependency { call(dependencyScope, dependency) }
  }

  override fun addApiDependency(dependency: String, sourceSet: String?) =
    addApiDependency(string(dependency), sourceSet)

  override fun addApiDependency(dependency: Expression, sourceSet: String?) = apply {
    addDependency("api", dependency, sourceSet)
  }

  override fun addCompileOnlyDependency(dependency: String, sourceSet: String?) =
    addCompileOnlyDependency(string(dependency), sourceSet)

  override fun addCompileOnlyDependency(dependency: Expression, sourceSet: String?) =
    addDependency("compileOnly", dependency, sourceSet)

  override fun addImplementationDependency(dependency: String, sourceSet: String?) =
    addImplementationDependency(string(dependency), sourceSet)

  override fun addImplementationDependency(dependency: Expression, sourceSet: String?) = apply {
    addDependency("implementation", dependency, sourceSet)
  }

  override fun addRuntimeOnlyDependency(dependency: String, sourceSet: String?) =
    addRuntimeOnlyDependency(string(dependency), sourceSet)

  override fun addRuntimeOnlyDependency(dependency: Expression, sourceSet: String?) = apply {
    addDependency("runtimeOnly", dependency, sourceSet)
  }

  override fun addTestImplementationDependency(dependency: String) =
    addImplementationDependency(dependency, sourceSet = "test")

  override fun addTestImplementationDependency(dependency: Expression) =
    addImplementationDependency(dependency, sourceSet = "test")

  override fun addTestRuntimeOnlyDependency(dependency: String) =
    addRuntimeOnlyDependency(dependency, sourceSet = "test")

  override fun addTestRuntimeOnlyDependency(dependency: Expression) =
    addRuntimeOnlyDependency(dependency, sourceSet = "test")

  override fun addBuildScriptClasspath(dependency: String) =
    addBuildScriptClasspath(string(dependency))

  override fun addBuildScriptClasspath(dependency: Expression) =
    withBuildScriptDependency { call("classpath", dependency) }

  override fun withBuildScriptMavenCentral() =
    withBuildScriptRepository { mavenCentral() }

  override fun withMavenCentral() =
    withRepository { mavenCentral() }

  override fun applyPlugin(plugin: String) =
    withPrefix {
      call("apply", "plugin" to plugin)
    }

  override fun applyPluginFrom(path: String) =
    withPrefix {
      call("apply", "from" to path)
    }

  override fun withPlugin(id: String, version: String?) =
    withPlugin {
      when (version) {
        null -> call("id", id)
        else -> infixCall(call("id", id), "version", string(version))
      }
    }

  override fun withJavaPlugin() =
    withPlugin("java")

  override fun withJavaLibraryPlugin() =
    withPlugin("java-library")

  override fun withIdeaPlugin() =
    withPlugin("idea")

  override fun withKotlinJvmPlugin() = withKotlinJvmPlugin(kotlinVersion)

  override fun withKotlinJsPlugin() =
    withPlugin("org.jetbrains.kotlin.js", kotlinVersion)

  override fun withKotlinMultiplatformPlugin() =
    withPlugin("org.jetbrains.kotlin.multiplatform", kotlinVersion)

  override fun withKotlinJvmToolchain(jvmTarget: Int): BSB = apply {
    withPostfix {
      call("kotlin") {
        // We use a code here to force the generator to use parenthesis in Groovy, to be in-line with the documentation
        code("jvmToolchain($jvmTarget)")
      }
    }
  }

  override fun withKotlinDsl(): BSB = apply {
    withMavenCentral()
    withPlugin {
      code("`kotlin-dsl`")
    }
  }

  override fun withGroovyPlugin() =
    withGroovyPlugin(groovyVersion)

  override fun withGroovyPlugin(version: String): BSB = apply {
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
    defaultJvmArgs: List<String>?
  ) = apply {
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

  override fun withJUnit() = apply {
    when (isJunit5Supported(gradleVersion)) {
      true -> withJUnit5()
      else -> withJUnit4()
    }
  }

  override fun withJUnit4() = apply {
    withMavenCentral()
    addTestImplementationDependency("junit:junit:$junit4Version")
  }

  override fun withJUnit5() = apply {
    assert(isJunit5Supported(gradleVersion))
    withMavenCentral()
    when (isPlatformDependencySupported(gradleVersion)) {
      true -> {
        addTestImplementationDependency(call("platform", "org.junit:junit-bom:$junit5Version"))
        addTestImplementationDependency("org.junit.jupiter:junit-jupiter")
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

  override fun targetCompatibility(level: String) = apply {
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

  override fun sourceCompatibility(level: String) = apply {
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

  override fun ScriptTreeBuilder.mavenCentral() = applyKt {
    val mavenRepositoryUrl = GradleEnvironment.Urls.MAVEN_REPOSITORY_URL
    if (mavenRepositoryUrl != null) {
      mavenRepository(mavenRepositoryUrl)
    }
    else {
      call("mavenCentral")
    }
  }
}