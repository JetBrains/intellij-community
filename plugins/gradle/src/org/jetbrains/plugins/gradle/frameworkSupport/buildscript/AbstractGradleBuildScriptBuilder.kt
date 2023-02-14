// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import com.intellij.openapi.util.text.StringUtil
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import java.io.File

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
    val scope = if (isSupportedJavaLibraryPlugin(gradleVersion)) "api" else "compile"
    addDependency(scope, dependency, sourceSet)
  }

  override fun addCompileOnlyDependency(dependency: String, sourceSet: String?) =
    addCompileOnlyDependency(string(dependency), sourceSet)

  override fun addCompileOnlyDependency(dependency: Expression, sourceSet: String?) =
    addDependency("compileOnly", dependency, sourceSet)

  override fun addImplementationDependency(dependency: String, sourceSet: String?) =
    addImplementationDependency(string(dependency), sourceSet)

  override fun addImplementationDependency(dependency: Expression, sourceSet: String?) = apply {
    val scope = if (isSupportedImplementationScope(gradleVersion)) "implementation" else "compile"
    addDependency(scope, dependency, sourceSet)
  }

  override fun addRuntimeOnlyDependency(dependency: String, sourceSet: String?) =
    addRuntimeOnlyDependency(string(dependency), sourceSet)

  override fun addRuntimeOnlyDependency(dependency: Expression, sourceSet: String?) = apply {
    val scope = if (isSupportedRuntimeOnlyScope(gradleVersion)) "runtimeOnly" else "runtime"
    addDependency(scope, dependency, sourceSet)
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

  override fun addBuildScriptClasspath(vararg dependencies: File) =
    addBuildScriptClasspath(call("files", dependencies.map { it.absolutePath }.map(::toSystemIndependentName).map(::argument)))

  override fun withBuildScriptMavenCentral() =
    withBuildScriptRepository {
      call("mavenCentral")
    }

  override fun withMavenCentral() =
    withRepository {
      call("mavenCentral")
    }

  override fun applyPlugin(plugin: String) =
    withPrefix {
      call("apply", argument("plugin", string(plugin)))
    }

  override fun applyPluginFrom(path: String) =
    withPrefix {
      call("apply", argument("from", string(path)))
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
    if (isSupportedJavaLibraryPlugin(gradleVersion))
      withPlugin("java-library")
    else
      withJavaPlugin()

  override fun withIdeaPlugin() =
    withPlugin("idea")

  override fun withKotlinJvmPlugin() =
    withPlugin("org.jetbrains.kotlin.jvm", kotlinVersion)

  override fun withKotlinJsPlugin() =
    withPlugin("org.jetbrains.kotlin.js", kotlinVersion)

  override fun withKotlinMultiplatformPlugin() =
    withPlugin("org.jetbrains.kotlin.multiplatform", kotlinVersion)

  override fun withGroovyPlugin() =
    withGroovyPlugin(groovyVersion)

  override fun withGroovyPlugin(version: String): BSB = apply {
    withPlugin("groovy")
    withMavenCentral()
    if (isSupportedGroovyApache(version)) {
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
    when (isSupportedJUnit5(gradleVersion)) {
      true -> withJUnit5()
      else -> withJUnit4()
    }
  }

  override fun withJUnit4() = apply {
    withMavenCentral()
    addTestImplementationDependency("junit:junit:$junit4Version")
  }

  override fun withJUnit5() = apply {
    assert(isSupportedJUnit5(gradleVersion))
    withMavenCentral()
    when (isSupportedPlatformDependency(gradleVersion)) {
      true -> {
        addTestImplementationDependency(call("platform", "org.junit:junit-bom:$junit5Version"))
        addTestImplementationDependency("org.junit.jupiter:junit-jupiter")
      }
      else -> {
        addTestImplementationDependency("org.junit.jupiter:junit-jupiter-api:$junit5Version")
        addTestRuntimeOnlyDependency("org.junit.jupiter:junit-jupiter-engine:$junit5Version")
      }
    }
    configureTestTask {
      call("useJUnitPlatform")
    }
  }
}