// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.lang.RuntimeException

class GradleBuildScriptBuilderEx : GradleBuildScriptBuilder() {
  fun withGradleIdeaExtPluginIfCan(version: String) = apply {
    val localDirWithJar = System.getenv("GRADLE_IDEA_EXT_PLUGIN_DIR")?.let(::File)
    if (localDirWithJar == null) {
      withGradleIdeaExtPlugin(version)
      return@apply
    }
    if (!localDirWithJar.exists()) throw RuntimeException("Directory $localDirWithJar not found")
    if (!localDirWithJar.isDirectory) throw RuntimeException("File $localDirWithJar is not directory")
    val template = "gradle-idea-ext-.+-SNAPSHOT\\.jar".toRegex()
    val jarFile = localDirWithJar.listFiles().find { it.name.matches(template) }
    if (jarFile == null) throw RuntimeException("Jar with gradle-idea-ext plugin not found")
    if (!jarFile.isFile) throw RuntimeException("Invalid jar file $jarFile")
    withLocalGradleIdeaExtPlugin(jarFile)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun withLocalGradleIdeaExtPlugin(jarFile: File) = apply {
    addBuildScriptRepository("mavenCentral()")
    addBuildScriptRepository("jcenter()")
    addBuildScriptDependency("classpath files('${FileUtil.toSystemIndependentName(jarFile.absolutePath)}')")
    addBuildScriptDependency("classpath 'com.google.code.gson:gson:2.8.2'")
    addBuildScriptDependency("classpath 'com.google.guava:guava:25.1-jre'")
    applyPlugin("'org.jetbrains.gradle.plugin.idea-ext'")
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun withGradleIdeaExtPlugin(version: String) = apply {
    addPlugin("id 'org.jetbrains.gradle.plugin.idea-ext' version '$version'")
  }

  fun withJavaPlugin() = apply {
    applyPlugin("'java'")
  }

  fun withKotlinPlugin(version: String) = apply {
    addBuildScriptPrefix("ext.kotlin_version = '$version'")
    addBuildScriptRepository("mavenCentral()")
    addBuildScriptDependency("classpath \"org.jetbrains.kotlin:kotlin-gradle-plugin:${'$'}kotlin_version\"")
    applyPlugin("'kotlin'")
  }

  fun withGroovyPlugin(version: String) = apply {
    applyPlugin("'groovy'")
    addRepository("mavenCentral()")
    addDependency("compile 'org.codehaus.groovy:groovy-all:$version'")
  }

  fun withJUnit(version: String) = apply {
    addRepository("mavenCentral()")
    addDependency("testCompile 'junit:junit:$version'")
  }
}