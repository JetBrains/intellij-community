// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.buildscript

import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import java.io.File

@Suppress("MemberVisibilityCanBePrivate", "unused")
abstract class AbstractGradleBuildScriptBuilder<BSB : GradleBuildScriptBuilder<BSB>>(
  gradleVersion: GradleVersion
) : AbstractGradleBuildScriptBuilderCore<BSB>(gradleVersion), GradleBuildScriptBuilder<BSB> {

  val kotlinVersion = if (isSupportedKotlin4(gradleVersion)) "1.4.32" else "1.3.50"
  val groovyVersion = "3.0.5"
  val junit4Version = "4.12"
  val junit5Version = "5.7.0"

  override fun addGroup(group: String) =
    withPrefix { assign("group", group) }

  override fun addVersion(version: String) =
    withPrefix { assign("version", version) }

  override fun addDependency(scope: String, dependency: String) =
    addDependency(scope, string(dependency))

  override fun addDependency(scope: String, dependency: Expression) =
    withDependency { call(scope, dependency) }

  private fun dependencyScope(sourceSet: String?, scope: String) =
    when (sourceSet) {
      null -> scope
      else -> sourceSet + scope.capitalize()
    }

  override fun addApiDependency(dependency: String, sourceSet: String?) =
    addApiDependency(string(dependency), sourceSet)

  override fun addApiDependency(dependency: Expression, sourceSet: String?) =
    addDependency(dependencyScope(sourceSet, "api"), dependency)

  override fun addImplementationDependency(dependency: String, sourceSet: String?) =
    addImplementationDependency(string(dependency), sourceSet)

  override fun addImplementationDependency(dependency: Expression, sourceSet: String?) =
    when (isSupportedImplementationScope(gradleVersion)) {
      true -> addDependency(dependencyScope(sourceSet, "implementation"), dependency)
      else -> addDependency(dependencyScope(sourceSet, "compile"), dependency)
    }

  override fun addRuntimeOnlyDependency(dependency: String, sourceSet: String?) =
    addRuntimeOnlyDependency(string(dependency), sourceSet)

  override fun addRuntimeOnlyDependency(dependency: Expression, sourceSet: String?) =
    when (isSupportedRuntimeOnlyScope(gradleVersion)) {
      true -> addDependency(dependencyScope(sourceSet, "runtimeOnly"), dependency)
      else -> addDependency(dependencyScope(sourceSet, "runtime"), dependency)
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

  override fun withPlugin(id: String, version: String?) = withPlugin {
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

  override fun withKotlinJvmPlugin() =
    withPlugin("org.jetbrains.kotlin.jvm", kotlinVersion)

  override fun withKotlinJsPlugin() =
    withPlugin("org.jetbrains.kotlin.js", kotlinVersion)

  override fun withKotlinMultiplatformPlugin() =
    withPlugin("org.jetbrains.kotlin.multiplatform", kotlinVersion)

  override fun withGroovyPlugin() = apply {
    withPlugin("groovy")
    withMavenCentral()
    addImplementationDependency("org.codehaus.groovy:groovy-all:$groovyVersion")
  }

  override fun withApplicationPlugin(mainClassName: String) = apply {
    withPlugin("application")
    withPostfix { assign("mainClassName", mainClassName) }
  }

  override fun withJUnit() = when (isSupportedJUnit5(gradleVersion)) {
    true -> withJUnit5()
    else -> withJUnit4()
  }

  override fun withJUnit4() = apply {
    withMavenCentral()
    addTestImplementationDependency("junit:junit:$junit4Version")
  }

  override fun withJUnit5() = apply {
    assert(isSupportedJUnit5(gradleVersion))
    withMavenCentral()
    addTestImplementationDependency("org.junit.jupiter:junit-jupiter-api:$junit5Version")
    addTestRuntimeOnlyDependency("org.junit.jupiter:junit-jupiter-engine:$junit5Version")
    withPostfix {
      call("test") {
        call("useJUnitPlatform")
      }
    }
  }
}