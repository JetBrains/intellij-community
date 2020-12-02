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
package com.android.tools.idea.gradle.dsl.model.files

import com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_DSL_FILE_APPLY_FROM_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_DSL_FILE_APPLY_FROM_BLOCK_APPLIED
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_DSL_FILE_INVOLVED_APPLIED_FILES
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_DSL_FILE_INVOLVED_APPLIED_FILES_APPLIED_FILE_ONE
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_DSL_FILE_INVOLVED_APPLIED_FILES_APPLIED_FILE_TWO
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_DSL_FILE_INVOLVED_FILES
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_DSL_FILE_INVOLVED_FILES_SUB
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_DSL_FILE_LIST_PROPERTIES_FROM_APPLIED_FILES
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_DSL_FILE_LIST_PROPERTIES_FROM_APPLIED_FILES_APPLIED_FILE_ONE
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_DSL_FILE_LIST_PROPERTIES_FROM_APPLIED_FILES_APPLIED_FILE_TWO
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_DSL_FILE_PROPERTIES_LIST
import com.android.tools.idea.gradle.dsl.TestFileNameImpl.GRADLE_DSL_FILE_PROPERTIES_LIST_SUB
import com.android.tools.idea.gradle.dsl.api.GradleFileModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.INTEGER_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BOOLEAN
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.INTEGER
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.REFERENCE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.PROPERTIES_FILE
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.VARIABLE
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Test
import java.io.File

class GradleDslFileTest : GradleFileModelTestCase() {
  @Test
  fun testInvolvedFiles() {
    val childProperties = "childPropProp1 = somevalue"
    val parentProperties = "parentPropProp1 = othervalue"
    writeToBuildFile(GRADLE_DSL_FILE_INVOLVED_FILES)
    writeToSubModuleBuildFile(GRADLE_DSL_FILE_INVOLVED_FILES_SUB)
    writeToSettingsFile(subModuleSettingsText)
    writeToPropertiesFile(parentProperties)
    writeToSubModulePropertiesFile(childProperties)

    val buildModel = subModuleGradleBuildModel

    run {
      val files = buildModel.involvedFiles
      assertSize(4, files)
      val expected = listOf(myBuildFile.path, mySubModuleBuildFile.path,
                            myPropertiesFile.path, mySubModulePropertiesFile.path)
      assertContainsElements(files.map { it.virtualFile.path }, expected.map { toSystemIndependentPath(it) })
    }
  }

  @Test
  fun testPropertiesList() {
    val childProperties = "childPropProp1 = somevalue"
    val parentProperties = "parentPropProp1 = othervalue"
    writeToBuildFile(GRADLE_DSL_FILE_PROPERTIES_LIST)
    writeToSubModuleBuildFile(GRADLE_DSL_FILE_PROPERTIES_LIST_SUB)
    writeToSettingsFile(subModuleSettingsText)
    writeToPropertiesFile(parentProperties)
    writeToSubModulePropertiesFile(childProperties)

    val buildModel = subModuleGradleBuildModel
    val files = buildModel.involvedFiles

    run {
      val properties = getFile(mySubModuleBuildFile, files).declaredProperties
      assertSize(4, properties)
      verifyPropertyModel(properties[0], STRING_TYPE, "value", STRING, VARIABLE, 0, "childVar2")
      verifyPropertyModel(properties[1], INTEGER_TYPE, 23, INTEGER, VARIABLE, 0, "childVar1")
      verifyPropertyModel(properties[2], STRING_TYPE, "childVar1", REFERENCE, REGULAR, 1, "childProp1")
      verifyListProperty(properties[3], listOf("hello", 23), REGULAR, 2, "childProp3")
    }

    run {
      // Parent file properties
      val properties = getFile(myBuildFile, files).declaredProperties
      assertSize(3, properties)
      verifyPropertyModel(properties[0], BOOLEAN_TYPE, true, BOOLEAN, VARIABLE, 0, "parentVar1")
      verifyPropertyModel(properties[1], STRING_TYPE, "parentVar1", REFERENCE, VARIABLE, 1, "parentVar2")
      verifyPropertyModel(properties[2], STRING_TYPE, "hello", STRING, REGULAR, 0, "parentProperty1")
    }

    run {
      // Parent properties file
      val properties = getFile(myPropertiesFile, files).declaredProperties
      assertSize(1, properties)
      verifyPropertyModel(properties[0], STRING_TYPE, "othervalue", STRING, PROPERTIES_FILE, 0, "parentPropProp1")
    }

    run {
      // Child properties file
      val properties = getFile(mySubModulePropertiesFile, files).declaredProperties
      assertSize(1, properties)
      verifyPropertyModel(properties[0], STRING_TYPE, "somevalue", STRING, PROPERTIES_FILE, 0, "childPropProp1")
    }
  }

  @Test
  fun testInvolvedAppliedFiles() {
    val b = writeToNewProjectFile("b", GRADLE_DSL_FILE_INVOLVED_APPLIED_FILES_APPLIED_FILE_ONE)
    val a = writeToNewProjectFile("a", GRADLE_DSL_FILE_INVOLVED_APPLIED_FILES_APPLIED_FILE_TWO)
    writeToBuildFile(GRADLE_DSL_FILE_INVOLVED_APPLIED_FILES)

    val buildModel = gradleBuildModel

    run {
      val files = buildModel.involvedFiles
      assertSize(4, files)
      val fileParent = myBuildFile.parent
      val expected =
        listOf(myBuildFile.path,
               fileParent.findChild(a)!!.path,
               fileParent.findChild(b)!!.path,
               fileParent.findChild("gradle.properties")!!.path)
      assertContainsElements(files.map { it.virtualFile.path }, expected.map { toSystemIndependentPath(it) })
    }
  }

  @Test
  fun testListPropertiesFromAppliedFiles() {
    val b = writeToNewProjectFile("b", GRADLE_DSL_FILE_LIST_PROPERTIES_FROM_APPLIED_FILES_APPLIED_FILE_ONE)
    val a = writeToNewProjectFile("a", GRADLE_DSL_FILE_LIST_PROPERTIES_FROM_APPLIED_FILES_APPLIED_FILE_TWO)
    writeToBuildFile(GRADLE_DSL_FILE_LIST_PROPERTIES_FROM_APPLIED_FILES)

    val buildModel = gradleBuildModel
    val files = buildModel.involvedFiles
    val fileParent = myBuildFile.parent

    run {
      val properties = getFile(myBuildFile, files).declaredProperties
      assertSize(2, properties)
      verifyPropertyModel(properties[0], STRING_TYPE, "goodbye", STRING, VARIABLE, 0, "goodbye")
      verifyPropertyModel(properties[1], INTEGER_TYPE, 5, INTEGER, REGULAR, 0, "prop5")
    }

    run {
      val properties = getFile(fileParent.findChild(a)!!, files).declaredProperties
      assertSize(2, properties)
      verifyPropertyModel(properties[0], STRING_TYPE, "hello", STRING, VARIABLE, 0, "hello")
      verifyPropertyModel(properties[1], BOOLEAN_TYPE, false, BOOLEAN, REGULAR, 0, "prop2")
    }

    run {
      val properties = getFile(fileParent.findChild(b)!!, files).declaredProperties
      assertSize(5, properties)
      verifyPropertyModel(properties[0], STRING_TYPE, "1", STRING, VARIABLE, 0, "var1")
      verifyPropertyModel(properties[1], INTEGER_TYPE, 2, INTEGER, VARIABLE, 0, "var2")
      verifyPropertyModel(properties[2], STRING_TYPE, "3", STRING, VARIABLE, 0, "var3")
      verifyListProperty(properties[3], listOf("1", 2, "3"), REGULAR, 3, "prop1")
      verifyPropertyModel(properties[4], BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "prop2")
    }
  }

  @Test
  fun testApplyFromBlock() {
    writeToNewProjectFile("a", GRADLE_DSL_FILE_APPLY_FROM_BLOCK_APPLIED)
    writeToBuildFile(GRADLE_DSL_FILE_APPLY_FROM_BLOCK)

    val buildModel = gradleBuildModel

    val property = buildModel.buildscript().dependencies().artifacts()[0].completeModel()
    verifyPropertyModel(property, STRING_TYPE, "value:name:2", STRING, REGULAR, 1)
  }

  fun getFile(file: VirtualFile, files: Set<GradleFileModel>) = files.first { toSystemIndependentPath(file.path) == it.virtualFile.path }

  companion object {
    fun toSystemIndependentPath(path: String): String {
      if (File.separatorChar != '/') {
        return path.replace(File.separatorChar, '/')
      }
      return path
    }
  }
}
