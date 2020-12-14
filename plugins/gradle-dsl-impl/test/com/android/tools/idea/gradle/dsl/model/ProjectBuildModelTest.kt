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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_APPLIED_FILES_SHARED
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_APPLIED_FILES_SHARED_APPLIED
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_APPLIED_FILES_SHARED_SUB
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_APPLY_NO_ROOT_BUILD_FILE
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_APPLY_NO_ROOT_BUILD_FILE_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER_APPLIED
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER_SUB
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_GET_MODEL_FROM_VIRTUAL_FILE
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_MULTIPLE_MODELS_PERSIST_CHANGES
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_MULTIPLE_MODELS_PERSIST_CHANGES_SUB
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_PROJECT_MODELS_SAVES_FILES
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_PROJECT_MODELS_SAVES_FILES_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_PROJECT_MODELS_SAVES_FILES_SUB
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_RESOLVES_CORRECT_FILE
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_RESOLVES_CORRECT_FILE_APPLIED
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_RESOLVES_CORRECT_FILE_APPLIED_SUB
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_RESOLVES_CORRECT_FILE_SUB
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_SETTINGS_FILE_UPDATES_CORRECTLY
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_SETTINGS_FILE_UPDATES_CORRECTLY_OTHER_SUB
import com.android.tools.idea.gradle.dsl.TestFileName.PROJECT_BUILD_MODEL_SETTINGS_FILE_UPDATES_CORRECTLY_SUB
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.INTEGER_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BOOLEAN
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.INTEGER
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase.runWriteAction
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File
import java.io.IOException

class ProjectBuildModelTest : GradleFileModelTestCase() {
  @Test
  fun testAppliedFilesShared() {
    writeToNewProjectFile("b", PROJECT_BUILD_MODEL_APPLIED_FILES_SHARED_APPLIED)
    writeToBuildFile(PROJECT_BUILD_MODEL_APPLIED_FILES_SHARED)
    writeToSubModuleBuildFile(PROJECT_BUILD_MODEL_APPLIED_FILES_SHARED_SUB)
    writeToSettingsFile(subModuleSettingsText)

    val projectModel = ProjectBuildModel.get(myProject)
    val parentBuildModel = projectModel.projectBuildModel!!
    val childBuildModel = projectModel.getModuleBuildModel(mySubModule)!!

    run {
      val parentProperty = parentBuildModel.ext().findProperty("property")
      verifyPropertyModel(parentProperty.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 1, "property")
      val childProperty = childBuildModel.ext().findProperty("childProperty")
      verifyPropertyModel(childProperty.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 1, "childProperty")
      val appliedProperty = childProperty.dependencies[0]
      verifyPropertyModel(appliedProperty.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 0, "greeting")

      // Alter the value of the applied file variable
      appliedProperty.setValue("goodbye")
      childProperty.rename("dodgy")

      verifyPropertyModel(appliedProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 0, "greeting")
      verifyPropertyModel(parentProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "property")
      verifyPropertyModel(childProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "dodgy")
    }

    assertFalse(parentBuildModel.isModified)
    assertTrue(childBuildModel.isModified)
    applyChangesAndReparse(projectModel)
    assertFalse(parentBuildModel.isModified)
    assertFalse(childBuildModel.isModified)

    run {
      val parentProperty = parentBuildModel.ext().findProperty("property")
      val childProperty = childBuildModel.ext().findProperty("dodgy")
      val appliedProperty = childProperty.dependencies[0]
      verifyPropertyModel(appliedProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 0, "greeting")
      verifyPropertyModel(parentProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "property")
      verifyPropertyModel(childProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "dodgy")
    }
  }

  @Test
  fun testApplyNoRootBuildFile() {
    writeToSubModuleBuildFile(PROJECT_BUILD_MODEL_APPLY_NO_ROOT_BUILD_FILE)
    writeToSettingsFile(subModuleSettingsText)

    // Delete the main build file
    runWriteAction<Unit, IOException> { myBuildFile.delete(this) }

    val pbm = ProjectBuildModel.get(myProject)
    assertNull(pbm.projectBuildModel)

    val buildModel = pbm.getModuleBuildModel(mySubModule)!!
    verifyPropertyModel(buildModel.ext().findProperty("prop"), INTEGER_TYPE, 1, INTEGER, REGULAR, 0)

    // Make a change
    buildModel.ext().findProperty("prop").setValue(5)

    // Make sure that applying the changes still affects the submodule build file
    applyChangesAndReparse(pbm)

    verifyFileContents(mySubModuleBuildFile, PROJECT_BUILD_MODEL_APPLY_NO_ROOT_BUILD_FILE_EXPECTED)
  }

  @Test
  fun testMultipleModelsPersistChanges() {
    writeToBuildFile(PROJECT_BUILD_MODEL_MULTIPLE_MODELS_PERSIST_CHANGES)
    writeToSubModuleBuildFile(PROJECT_BUILD_MODEL_MULTIPLE_MODELS_PERSIST_CHANGES_SUB)

    val projectModel = ProjectBuildModel.get(myProject)
    val childModelOne = projectModel.getModuleBuildModel(mySubModule)!!
    val childModelTwo = projectModel.getModuleBuildModel(mySubModule)!!
    val parentModelOne = projectModel.projectBuildModel!!
    val parentModelTwo = projectModel.projectBuildModel!!

    // Edit the properties in one of the models.
    run {
      val parentPropertyModel = parentModelTwo.ext().findProperty("prop")
      verifyPropertyModel(parentPropertyModel.resolve(), STRING_TYPE, "Hello i am true!", STRING, REGULAR, 1)
      val childPropertyModel = childModelOne.ext().findProperty("prop1")
      verifyPropertyModel(childPropertyModel.resolve(), STRING_TYPE, "boo", STRING, REGULAR, 0)

      // Change values on each file.
      parentPropertyModel.dependencies[0].setValue(false)
      childPropertyModel.setValue("ood")

      // Check that the properties have been updated in the original models
      verifyPropertyModel(parentPropertyModel.resolve(), STRING_TYPE, "Hello i am false!", STRING, REGULAR, 1)
      verifyPropertyModel(childPropertyModel.resolve(), STRING_TYPE, "ood", STRING, REGULAR, 0)
      // Check that the properties have been updated in the other models
      val otherParentPropertyModel = parentModelOne.ext().findProperty("prop")
      val otherChildPropertyModel = childModelTwo.ext().findProperty("prop1")
      verifyPropertyModel(otherParentPropertyModel.resolve(), STRING_TYPE, "Hello i am false!", STRING, REGULAR, 1)
      verifyPropertyModel(otherChildPropertyModel.resolve(), STRING_TYPE, "ood", STRING, REGULAR, 0)
    }

    applyChangesAndReparse(projectModel)

    run {
      val parentPropertyModel = parentModelTwo.ext().findProperty("prop")
      val childPropertyModel = childModelOne.ext().findProperty("prop1")
      // Check that the properties have been updated in the original models
      verifyPropertyModel(parentPropertyModel.resolve(), STRING_TYPE, "Hello i am false!", STRING, REGULAR, 1)
      verifyPropertyModel(childPropertyModel.resolve(), STRING_TYPE, "ood", STRING, REGULAR, 0)
      // Check that the properties have been updated in the other models
      val otherParentPropertyModel = parentModelOne.ext().findProperty("prop")
      val otherChildPropertyModel = childModelTwo.ext().findProperty("prop1")
      verifyPropertyModel(otherParentPropertyModel.resolve(), STRING_TYPE, "Hello i am false!", STRING, REGULAR, 1)
      verifyPropertyModel(otherChildPropertyModel.resolve(), STRING_TYPE, "ood", STRING, REGULAR, 0)
    }
  }

  @Test
  fun testApplyResolvesCorrectFile() {
    // The sub-module applies a sub-module Gradle file which in turn applies a Gradle file from the root project directory.
    writeToBuildFile(PROJECT_BUILD_MODEL_RESOLVES_CORRECT_FILE)
    writeToNewProjectFile("applied", PROJECT_BUILD_MODEL_RESOLVES_CORRECT_FILE_APPLIED)
    writeToSubModuleBuildFile(PROJECT_BUILD_MODEL_RESOLVES_CORRECT_FILE_SUB)
    writeToNewSubModuleFile("applied", PROJECT_BUILD_MODEL_RESOLVES_CORRECT_FILE_APPLIED_SUB)
    writeToSettingsFile(subModuleSettingsText)

    // This should correctly resolve the variable
    val projectModel = ProjectBuildModel.get(myProject)
    val buildModel = projectModel.getModuleBuildModel(mySubModule)!!

    val prop = buildModel.ext().findProperty("prop")
    verifyPropertyModel(prop, STRING_TYPE, "value", STRING, REGULAR, 0)
  }

  @Test
  fun testSettingsFileUpdatesCorrectly() {
    writeToBuildFile(PROJECT_BUILD_MODEL_SETTINGS_FILE_UPDATES_CORRECTLY)
    writeToSubModuleBuildFile(PROJECT_BUILD_MODEL_SETTINGS_FILE_UPDATES_CORRECTLY_SUB)
    writeToSettingsFile(subModuleSettingsText)
    val newModule = writeToNewSubModule("lib", PROJECT_BUILD_MODEL_SETTINGS_FILE_UPDATES_CORRECTLY_OTHER_SUB, "")

    val projectModel = ProjectBuildModel.get(myProject)
    val parentBuildModel = projectModel.projectBuildModel!!
    val childBuildModel = projectModel.getModuleBuildModel(File(mySubModule.moduleFilePath).parentFile)!!
    val otherChildBuildModel = projectModel.getModuleBuildModel(newModule)!!
    val settingsModel = projectModel.projectSettingsModel!!

    run {
      // Check the child build models are correct.
      val childPropertyModel = childBuildModel.ext().findProperty("moduleProp")
      verifyPropertyModel(childPropertyModel, STRING_TYPE, "one", STRING, REGULAR, 0, "moduleProp")
      val otherChildPropertyModel = otherChildBuildModel.ext().findProperty("otherModuleProp")
      verifyPropertyModel(otherChildPropertyModel, STRING_TYPE, "two", STRING, REGULAR, 0, "otherModuleProp")
      // Change the module paths are correct.
      val paths = settingsModel.modulePaths()
      assertThat(paths, hasItems(":", ":${SUB_MODULE_NAME}"))
      val parentBuildModelTwo = settingsModel.moduleModel(":")!!
      // Check that this model has the same view as one we obtained from the project model.
      val propertyModel = parentBuildModelTwo.ext().findProperty("parentProp")
      verifyPropertyModel(propertyModel.resolve(), STRING_TYPE, "zero", STRING, REGULAR, 0, "parentProp")
      val oldPropertyModel = parentBuildModel.ext().findProperty("parentProp")
      verifyPropertyModel(oldPropertyModel.resolve(), STRING_TYPE, "zero", STRING, REGULAR, 0, "parentProp")
      propertyModel.setValue(true)
      verifyPropertyModel(propertyModel.resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "parentProp")
      verifyPropertyModel(oldPropertyModel.resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "parentProp")

      // Add the new path to the settings model.
      settingsModel.addModulePath(":lib")
      val newPaths = settingsModel.modulePaths()
      assertThat(newPaths, hasItems(":", ":${SUB_MODULE_NAME}", ":lib"))
    }

    applyChangesAndReparse(projectModel)

    run {
      val paths = settingsModel.modulePaths()
      assertThat(paths, hasItems(":", ":${SUB_MODULE_NAME}", ":lib"))
    }
  }

  @Test
  fun testProjectModelSavesFiles() {
    writeToSubModuleBuildFile(PROJECT_BUILD_MODEL_PROJECT_MODELS_SAVES_FILES_SUB)
    writeToBuildFile(PROJECT_BUILD_MODEL_PROJECT_MODELS_SAVES_FILES)
    writeToSettingsFile(subModuleSettingsText)
    var pbm = ProjectBuildModel.get(myProject)
    var buildModel = pbm.getModuleBuildModel(mySubModule)
    var optionsModel = buildModel!!.android().defaultConfig().externalNativeBuild().cmake()
    optionsModel.arguments().addListValue().setValue("-DCMAKE_MAKE_PROGRAM=////")
    verifyListProperty(optionsModel.arguments(), listOf("-DCMAKE_MAKE_PROGRAM=////"))

    applyChangesAndReparse(pbm)

    pbm = ProjectBuildModel.get(myProject)
    buildModel = pbm.getModuleBuildModel(mySubModule)
    optionsModel = buildModel!!.android().defaultConfig().externalNativeBuild().cmake()
    verifyListProperty(optionsModel.arguments(), listOf("-DCMAKE_MAKE_PROGRAM=////"))

    verifyFileContents(mySubModuleBuildFile, PROJECT_BUILD_MODEL_PROJECT_MODELS_SAVES_FILES_EXPECTED)
  }

  @Test
  fun testGetModelFromVirtualFile() {
    writeToBuildFile(PROJECT_BUILD_MODEL_GET_MODEL_FROM_VIRTUAL_FILE)

    val pbm = ProjectBuildModel.get(myProject)
    val buildModel = pbm.getModuleBuildModel(myBuildFile)
    assertNotNull(buildModel)
    verifyPropertyModel(buildModel.android().compileSdkVersion(), STRING_TYPE, "28", STRING, REGULAR, 0)
  }

  @Test
  fun testEnsureParsingAppliedFileInSubmoduleFolder() {
    writeToSubModuleBuildFile(PROJECT_BUILD_MODEL_ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER_SUB)
    writeToBuildFile(PROJECT_BUILD_MODEL_ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER)
    writeToSettingsFile(subModuleSettingsText)
    writeToNewSubModuleFile("a", PROJECT_BUILD_MODEL_ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER_APPLIED)

    val pbm = ProjectBuildModel.get(myProject)
    val buildModel = pbm.getModuleBuildModel(myModule)

    val pluginModel = buildModel!!.buildscript().dependencies().artifacts()[0].completeModel()
    assertEquals("com.android.tools.build:gradle:${'$'}version", pluginModel.forceString())
  }

  @Test
  fun testProjectModelGetFile() {
    // We reuse a build file here since we just need any file for this test.
    writeToSubModuleBuildFile(PROJECT_BUILD_MODEL_ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER_SUB)
    writeToBuildFile(PROJECT_BUILD_MODEL_ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER)
    writeToSettingsFile(subModuleSettingsText)
    writeToNewSubModuleFile("a", PROJECT_BUILD_MODEL_ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER_APPLIED)

    val pbm = ProjectBuildModel.get(myProject)
    val mainBuildModel = pbm.getModuleBuildModel(myModule)!!
    val subBuildModel = pbm.getModuleBuildModel(mySubModule)!!
    val settingModel = pbm.projectSettingsModel!!

    val mainPsiFile = mainBuildModel.psiFile!!
    val subPsiFile = subBuildModel.psiFile!!
    val settingFile = settingModel.psiFile!!
    assertEquals(mainPsiFile.virtualFile, mainBuildModel.virtualFile)
    assertEquals(subPsiFile.virtualFile, subBuildModel.virtualFile)
    assertEquals(settingFile.virtualFile, settingModel.virtualFile)
  }
}
