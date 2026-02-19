// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.model.RepositoryModels
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoriesModelBuilderTest(gradleVersion: String) : AbstractModelBuilderTest(gradleVersion) {

  @Test
  fun `test repository order is the same as in the build script`() {
    createProjectFile("build.gradle", GradleBuildScriptBuilder.create(gradleVersion, false)
      .addRepository("""
        maven { 
          name = 'YYY'
          url = file('lib') 
        }
      """.trimIndent())
      .addRepository("mavenCentral()")
      .addRepository("""
        maven { 
          name = 'MyTestRepo'
          url = file('lib') 
        }
      """.trimIndent())
      .addRepository("mavenLocal()")
      .addRepository("""
        maven { 
          name = 'AAA'
          url = file('lib') 
        }
      """.trimIndent())
      .generate()
    )

    val holder = runBuildAction(RepositoryModels::class.java)
    val repositoryModels = holder.getRootModel(RepositoryModels::class.java)
                           ?: throw IllegalStateException("RepositoryModels should be provided in the project")
    val repositoryNames = repositoryModels.repositories.map { it.name }
      .toList()
    assertEquals(listOf("YYY", "MavenRepo", "MyTestRepo", "MavenLocal", "AAA"), repositoryNames)
  }
}