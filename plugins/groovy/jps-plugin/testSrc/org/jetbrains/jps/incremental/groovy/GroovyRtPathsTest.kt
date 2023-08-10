// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.groovy

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.directoryContent
import org.jetbrains.groovy.compiler.rt.GroovyRtJarPaths
import org.junit.Assert
import org.junit.Test
import java.io.File

class GroovyRtPathsTest {
  @Test
  fun runningFromSources() {
    val out = directoryContent {
      dir("intellij.groovy.constants.rt") {}
      dir("intellij.groovy.jps") {}
      dir("intellij.groovy.rt") {}
      dir("intellij.groovy.rt.classLoader") {}
    }.generateInTempDir().toFile()
    val roots = GroovyRtJarPaths.getGroovyRtRoots(File(out, "intellij.groovy.jps"), true)
    assertSameFiles(roots, File(out, "intellij.groovy.rt"), File(out, "intellij.groovy.constants.rt"),
      File(out, "intellij.groovy.rt.classLoader"))
  }

  @Test
  fun pluginDistribution() {
    val lib = directoryContent {
      file("groovy-jps.jar")
      file("groovy-constants-rt.jar")
      file("groovy-rt.jar")
    }.generateInTempDir().toFile()
    val roots = GroovyRtJarPaths.getGroovyRtRoots(File(lib, "groovy-jps.jar"), true)
    assertSameFiles(roots,
      File(lib, "groovy-rt.jar"), File(lib, "groovy-constants-rt.jar"), File(lib, "groovy-rt-class-loader.jar"))
  }

  @Test
  fun buildScriptDependencies() {
    val lib = directoryContent {
      file("groovy-constants-rt-193.239.jar")
      file("groovy-rt-193.239.jar")
      file("groovy-jps-193.239.jar")
    }.generateInTempDir().toFile()
    val roots = GroovyRtJarPaths.getGroovyRtRoots(File(lib, "groovy-jps-193.239.jar"), false)
    assertSameFiles(roots, File(lib, "groovy-rt-193.239.jar"), File(lib, "groovy-constants-rt-193.239.jar"))
  }

  @Test
  fun mavenArtifactsInRepository() {
    val repo = directoryContent {
      dir("com") {
        dir("jetbrains") {
          dir("intellij") {
            dir("groovy") {
              dir("groovy-constants-rt") {
                dir("193.239") { file("groovy-constants-rt-193.239.jar") }
              }
              dir("groovy-jps") {
                dir("193.239") { file("groovy-jps-193.239.jar") }
              }
              dir("groovy-rt") {
                dir("193.239") { file("groovy-rt-193.239.jar") }
              }
            }
          }
        }
      }
    }.generateInTempDir().toFile()
    val roots = GroovyRtJarPaths.getGroovyRtRoots(
      File(repo, "com/jetbrains/intellij/groovy/groovy-jps/193.239/groovy-jps-193.239.jar"),
      true)
    assertSameFiles(roots, File(repo, "com/jetbrains/intellij/groovy/groovy-rt/193.239/groovy-rt-193.239.jar"),
                    File(repo, "com/jetbrains/intellij/groovy/groovy-constants-rt/193.239/groovy-constants-rt-193.239.jar"),
                    File(repo, "com/jetbrains/intellij/groovy/groovy-rt-class-loader/193.239/groovy-rt-class-loader-193.239.jar"))
  }

  private fun assertSameFiles(paths: List<String>, vararg file: File) {
    val actualPaths = paths.mapTo(HashSet()) { FileUtil.toSystemIndependentName(it) }
    val expectedPaths = file.mapTo(HashSet()) { FileUtil.toSystemIndependentName(it.absolutePath) }
    Assert.assertEquals(expectedPaths, actualPaths)
  }
}
