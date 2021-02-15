/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.model.dependencies

import com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEPENDENCIES_ADD_NON_IDENTIFIER_CONFIGURATION_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEPENDENCIES_ALL_DEPENDENCIES
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEPENDENCIES_KOTLIN_DEPENDENCIES
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEPENDENCIES_NON_IDENTIFIER_CONFIGURATION
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEPENDENCIES_REMOVE_JAR_DEPENDENCIES
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.DEPENDENCIES_SET_NON_IDENTIFIER_CONFIGURATION_EXPECTED
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class DependenciesTest : GradleFileModelTestCase() {
  @Test
  fun testAllDependencies() {
    writeToBuildFile(DEPENDENCIES_ALL_DEPENDENCIES)

    val buildModel = gradleBuildModel

    val deps = buildModel.dependencies().all()
    assertSize(13, deps)
    run {
      val dep = deps[0] as FileTreeDependencyModel
      assertThat(dep.configurationName(), equalTo("api"))
      assertThat(dep.dir().toString(), equalTo("libs"))
      assertThat(dep.includes().toList()?.map { it.toString() }, equalTo(listOf("*.jar")))
      assertThat(dep.excludes().toList(), nullValue())
    }
    run {
      val dep = deps[1] as ArtifactDependencyModel
      assertThat(dep.configurationName(), equalTo("implementation"))
      assertThat(dep.compactNotation(), equalTo("com.example.libs:lib1:0.+"))
    }
    run {
      val dep = deps[2] as ArtifactDependencyModel
      assertThat(dep.configurationName(), equalTo("api"))
      assertThat(dep.compactNotation(), equalTo("com.android.support:appcompat-v7:+"))
    }
    run {
      val dep = deps[3] as FileDependencyModel
      assertThat(dep.configurationName(), equalTo("compile"))
      assertThat(dep.file().toString(), equalTo("lib1.jar"))
    }
    run {
      val dep = deps[4] as FileDependencyModel
      assertThat(dep.configurationName(), equalTo("compile"))
      assertThat(dep.file().toString(), equalTo("lib2.jar"))
    }
    run {
      val dep = deps[5] as FileDependencyModel
      assertThat(dep.configurationName(), equalTo("compile"))
      assertThat(dep.file().toString(), equalTo("lib3.aar"))
    }
    run {
      val dep = deps[6] as FileDependencyModel
      assertThat(dep.configurationName(), equalTo("implementation"))
      assertThat(dep.file().toString(), equalTo("lib4.aar"))
    }
    run {
      val dep = deps[7] as ModuleDependencyModel
      assertThat(dep.configurationName(), equalTo("debugImplementation"))
      assertThat(dep.name(), equalTo("javalib1"))
    }
    run {
      val dep = deps[8] as ArtifactDependencyModel
      assertThat(dep.configurationName(), equalTo("releaseImplementation"))
      assertThat(dep.compactNotation(), equalTo("some:lib:1.0"))
    }
    run {
      val dep = deps[9] as FileDependencyModel
      assertThat(dep.configurationName(), equalTo("releaseImplementation"))
      assertThat(dep.file().toString(), equalTo("lib5.jar"))
    }
    run {
      val dep = deps[10] as ModuleDependencyModel
      assertThat(dep.configurationName(), equalTo("releaseImplementation"))
      assertThat(dep.name(), equalTo("lib3"))
    }
    run {
      val dep = deps[11] as FileTreeDependencyModel
      assertThat(dep.configurationName(), equalTo("releaseImplementation"))
      assertThat(dep.dir().toString(), equalTo("libz"))
      assertThat(dep.includes().toList()?.map { it.toString() }, equalTo(listOf("*.jar")))
      assertThat(dep.excludes().toList(), nullValue())
    }
    run {
      val dep = deps[12] as ArtifactDependencyModel
      assertThat(dep.configurationName(), equalTo("releaseImplementation"))
      assertThat(dep.compactNotation(), equalTo("org.springframework:spring-core:2.5"))
    }
  }

  @Test
  fun testRemoveJarDependencies() {
    writeToBuildFile(DEPENDENCIES_REMOVE_JAR_DEPENDENCIES)

    val buildModel = gradleBuildModel

    val deps = buildModel.dependencies().all()
    assertSize(3, deps)
    val fileTree = let {
      val dep = deps[0] as FileTreeDependencyModel
      assertThat(dep.configurationName(), equalTo("api"))
      assertThat(dep.dir().toString(), equalTo("libs"))
      assertThat(dep.includes().toList()?.map { it.toString() }, equalTo(listOf("*.jar")))
      assertThat(dep.excludes().toList(), nullValue())
      dep
    }
    val files = let {
      val dep = deps[2] as FileDependencyModel
      assertThat(dep.configurationName(), equalTo("compile"))
      assertThat(dep.file().toString(), equalTo("lib1.jar"))
      dep
    }
    buildModel.dependencies().remove(fileTree)
    buildModel.dependencies().remove(files)
    assertSize(1, buildModel.dependencies().all())
  }

  @Test
  fun testKotlinDependencies() {
    writeToBuildFile(DEPENDENCIES_KOTLIN_DEPENDENCIES)

    val buildModel = gradleBuildModel

    val deps = buildModel.dependencies().all()
    assertSize(2, deps)

    run {
      val dep = deps[0] as ArtifactDependencyModel
      assertThat(dep.configurationName(), equalTo("implementation"))
      assertThat(dep.compactNotation(), equalTo("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.3.1"))
    }

    run {
      val dep = deps[1] as ArtifactDependencyModel
      assertThat(dep.configurationName(), equalTo("implementation"))
      assertThat(dep.compactNotation(), equalTo("org.jetbrains.kotlin:kotlin-android"))
    }
  }

  @Test
  fun testAddNonIdentifierConfiguration() {
    writeToBuildFile(DEPENDENCIES_NON_IDENTIFIER_CONFIGURATION)

    val buildModel = gradleBuildModel

    val dependencies = buildModel.dependencies()
    dependencies.addArtifact("dotted.buildtypeImplementation", "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.3.1")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, DEPENDENCIES_ADD_NON_IDENTIFIER_CONFIGURATION_EXPECTED)

    val artifacts = buildModel.dependencies().artifacts()
    assertSize(2, artifacts)
    assertThat(artifacts[0].configurationName(), equalTo("implementation"))
    assertThat(artifacts[0].compactNotation(), equalTo("com.android.support:appcompat-v7:+"))
    assertThat(artifacts[1].configurationName(), equalTo("dotted.buildtypeImplementation"))
    assertThat(artifacts[1].compactNotation(), equalTo("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.3.1"))
  }

  @Test
  fun testSetNonIdentifierConfiguration() {
    writeToBuildFile(DEPENDENCIES_NON_IDENTIFIER_CONFIGURATION)

    val buildModel = gradleBuildModel

    var artifacts = buildModel.dependencies().artifacts()
    assertSize(1, artifacts)
    artifacts[0].setConfigurationName("dotted.buildtypeImplementation")

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, DEPENDENCIES_SET_NON_IDENTIFIER_CONFIGURATION_EXPECTED)

    artifacts = buildModel.dependencies().artifacts()
    assertSize(1, artifacts)
    assertThat(artifacts[0].configurationName(), equalTo("dotted.buildtypeImplementation"))
    assertThat(artifacts[0].compactNotation(), equalTo("com.android.support:appcompat-v7:+"))
  }
}
