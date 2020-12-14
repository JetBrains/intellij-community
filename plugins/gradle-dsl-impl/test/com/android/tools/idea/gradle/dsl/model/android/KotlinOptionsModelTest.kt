/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.intellij.pom.java.LanguageLevel
import org.junit.Test
import java.lang.IllegalArgumentException

class KotlinOptionsModelTest : GradleFileModelTestCase() {
  @Test
  fun parse() {
    writeToBuildFile(TestFileName.KOTLIN_OPTIONS_MODEL_BLOCK)

    val android = gradleBuildModel.android()
    val kotlinOptions = android.kotlinOptions()
    assertEquals("jvmTarget", LanguageLevel.JDK_1_6, kotlinOptions.jvmTarget().toLanguageLevel())
  }

  @Test
  fun `add valid JVM target`() {
    writeToBuildFile(TestFileName.KOTLIN_OPTIONS_MODEL_ADD)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    var kotlinOptions = android.kotlinOptions()
    assertMissingProperty(kotlinOptions.jvmTarget())
    kotlinOptions.jvmTarget().setLanguageLevel(LanguageLevel.JDK_1_8)
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFileName.KOTLIN_OPTIONS_MODEL_ADD_EXPECTED)
    android = buildModel.android()
    kotlinOptions = android.kotlinOptions()
    assertEquals("jvmTarget", LanguageLevel.JDK_1_8, kotlinOptions.jvmTarget().toLanguageLevel())
  }

  @Test
  fun `add unknown JVM target`() {
    writeToBuildFile(TestFileName.KOTLIN_OPTIONS_MODEL_ADD_UNKNOWN_TARGET)

    val android = gradleBuildModel.android()
    val kotlinOptions = android.kotlinOptions()
    assertMissingProperty(kotlinOptions.jvmTarget())
    assertThrows<IllegalArgumentException>(IllegalArgumentException::class.java) {
      kotlinOptions.jvmTarget().setLanguageLevel(LanguageLevel.JDK_1_7)
    }
  }

  @Test
  fun remove() {
    writeToBuildFile(TestFileName.KOTLIN_OPTIONS_MODEL_REMOVE)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    var kotlinOptions = android.kotlinOptions()
    kotlinOptions.jvmTarget().delete()
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFileName.KOTLIN_OPTIONS_MODEL_REMOVE_EXPECTED)
    android = buildModel.android()
    kotlinOptions = android.kotlinOptions()
    checkForInValidPsiElement(kotlinOptions, KotlinOptionsModelImpl::class.java)
    assertMissingProperty(kotlinOptions.jvmTarget())
  }

  @Test
  fun modify() {
    writeToBuildFile(TestFileName.KOTLIN_OPTIONS_MODEL_MODIFY)

    val buildModel = gradleBuildModel
    var android = buildModel.android()
    var kotlinOptions = android.kotlinOptions()
    assertEquals("jvmTarget", LanguageLevel.JDK_1_6, kotlinOptions.jvmTarget().toLanguageLevel())
    kotlinOptions.jvmTarget().setLanguageLevel(LanguageLevel.JDK_1_9)
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFileName.KOTLIN_OPTIONS_MODEL_MODIFY_EXPECTED)
    android = buildModel.android()
    kotlinOptions = android.kotlinOptions()
    assertEquals("jvmTarget", LanguageLevel.JDK_1_9, kotlinOptions.jvmTarget().toLanguageLevel())
  }
}