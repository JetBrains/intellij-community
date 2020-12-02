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
package com.android.tools.idea.gradle.dsl.model.ext

import com.android.tools.idea.gradle.dsl.TestFileNameImpl.PROPERTY_MODIFIED_TEST_FILE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr
import com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.junit.Test

class PropertyModifiedTest : GradleFileModelTestCase() {
  @Test
  fun testIsModifiedLiterals() {
    writeToBuildFile(PROPERTY_MODIFIED_TEST_FILE)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    assertFalse(extModel.findProperty("prop1").isModified)
    val prop2Model = extModel.findProperty("prop2")
    // Change value
    prop2Model.setValue("77")
    assertTrue(prop2Model.isModified)
    // Other models made from the same element should also be modified.
    assertTrue(extModel.findProperty("prop2").isModified)
    // Set the value back
    prop2Model.setValue(25)
    assertFalse(prop2Model.isModified)
    assertFalse(extModel.findProperty("prop2").isModified)
  }

  @Test
  fun testIsModifiedReferences() {
    writeToBuildFile(PROPERTY_MODIFIED_TEST_FILE)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val prop3Model = extModel.findProperty("prop3")
    val prop2Model = extModel.findProperty("prop2")
    val prop1Model = extModel.findProperty("prop1")
    assertFalse(prop3Model.isModified)
    assertFalse(prop2Model.isModified)
    assertFalse(prop1Model.isModified)

    prop3Model.setValue(ReferenceTo("prop1"))
    assertTrue(prop3Model.isModified)
    assertFalse(prop2Model.isModified)
    assertFalse(prop1Model.isModified)
    assertTrue(extModel.findProperty("prop3").isModified)

    // Set value back
    prop3Model.setValue(ReferenceTo("prop2"))
    assertFalse(prop3Model.isModified)
    assertFalse(prop2Model.isModified)
    assertFalse(prop1Model.isModified)
    assertFalse(extModel.findProperty("prop3").isModified)

    // Setting to literal switches to false
    prop3Model.setValue(true)
    assertTrue(prop3Model.isModified)
    assertFalse(prop2Model.isModified)
    assertFalse(prop1Model.isModified)

    // Setting back to a reference
    prop3Model.setValue(ReferenceTo("prop2"))
    assertFalse(prop3Model.isModified)
    assertFalse(prop2Model.isModified)
    assertFalse(prop1Model.isModified)
  }

  @Test
  fun testIsModifiedReferenceDoesNotAffectReferer() {
    writeToBuildFile(PROPERTY_MODIFIED_TEST_FILE)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val prop3Model = extModel.findProperty("prop3")
    val prop2Model = extModel.findProperty("prop2")
    assertFalse(prop3Model.isModified)
    assertFalse(prop2Model.isModified)

    prop2Model.setValue("hello")

    assertTrue(prop2Model.isModified)
    assertFalse(prop3Model.isModified)

    prop2Model.setValue(25)

    assertFalse(prop2Model.isModified)
    assertFalse(prop3Model.isModified)
  }

  @Test
  fun testIsModifiedRename() {
    writeToBuildFile(PROPERTY_MODIFIED_TEST_FILE)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val prop3Model = extModel.findProperty("prop3")
    assertFalse(prop3Model.isModified)
    prop3Model.rename("prop9")
    assertTrue(prop3Model.isModified)
    assertTrue(extModel.findProperty("prop9").isModified)

    // Rename back
    prop3Model.rename("prop3")
    assertFalse(prop3Model.isModified)
    assertFalse(extModel.findProperty("prop3").isModified)
  }

  @Test
  fun testIsModifiedListValue() {
    writeToBuildFile(PROPERTY_MODIFIED_TEST_FILE)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val prop5Model = extModel.findProperty("prop5")
    assertFalse(prop5Model.isModified)
    val listVal = prop5Model.toList()!![0]!!
    assertFalse(listVal.isModified)
    listVal.setValue("val3")
    assertTrue(prop5Model.isModified)
    assertTrue(listVal.isModified)

    listVal.setValue("val1")
    assertFalse(prop5Model.isModified)
    assertFalse(listVal.isModified)

    val otherListVal = prop5Model.toList()!![1]
    assertFalse(otherListVal.isModified)
    otherListVal.setValue(ReferenceTo("prop5[0]"))
    assertTrue(prop5Model.isModified)
    assertTrue(otherListVal.isModified)

    otherListVal.setValue("val2")
    assertFalse(prop5Model.isModified)
    assertFalse(otherListVal.isModified)
  }

  @Test
  fun testIsModifiedAddListValue() {
    writeToBuildFile(PROPERTY_MODIFIED_TEST_FILE)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val prop5Model = extModel.findProperty("prop5")
    assertFalse(prop5Model.isModified)
    val newListValue = prop5Model.addListValue()
    // List values are created in the call to addListValue
    assertTrue(newListValue.isModified)
    newListValue.setValue(55)
    assertTrue(newListValue.isModified)
    assertTrue(prop5Model.isModified)

    newListValue.delete()
    assertFalse(prop5Model.isModified)
    assertFalse(newListValue.isModified)
  }

  @Test
  fun testIsModifiedRemoveListValue() {
    writeToBuildFile(PROPERTY_MODIFIED_TEST_FILE)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val prop5Model = extModel.findProperty("prop5")
    assertFalse(prop5Model.isModified)
    val listVal = prop5Model.getListValue("val2")!!
    assertFalse(listVal.isModified)
    // Remove an element
    listVal.delete()
    assertTrue(listVal.isModified)
    assertTrue(prop5Model.isModified)

    // Add it back
    val newListVal = prop5Model.addListValueAt(1)
    assertTrue(newListVal.isModified)
    newListVal.setValue("val2")
    assertFalse(newListVal.isModified)
    assertFalse(prop5Model.isModified)
  }

  @Test
  fun testIsModifiedSetMapValueAndKey() {
    writeToBuildFile(PROPERTY_MODIFIED_TEST_FILE)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val prop4Model = extModel.findProperty("prop4")
    assertFalse(prop4Model.isModified)
    val mapVal = prop4Model.toMap()!!["key"]!!
    assertFalse(mapVal.isModified)
    // Edit value
    mapVal.setValue("hello")
    assertTrue(mapVal.isModified)
    assertTrue(prop4Model.isModified)

    // Rename the value
    mapVal.rename("key1")
    assertTrue(mapVal.isModified)
    assertTrue(prop4Model.isModified)
    // Set the value back
    mapVal.setValue("val")
    assertTrue(mapVal.isModified)
    assertTrue(prop4Model.isModified)
    // Set the name back
    mapVal.rename("key")
    assertFalse(mapVal.isModified)
    assertFalse(prop4Model.isModified)
  }

  @Test
  fun testIsModifiedChangeMethodArg() {
    writeToBuildFile(PROPERTY_MODIFIED_TEST_FILE)

    val buildModel = gradleBuildModel

    val storeFile = buildModel.android().signingConfigs()[0]!!.storeFile()
    assertFalse(storeFile.isModified)
    storeFile.setValue("some_file_value.txt")

    assertTrue(storeFile.isModified)
    storeFile.setValue("my_file.txt")

    assertFalse(storeFile.isModified)
  }

  @Test
  fun testIsModifiedMultiType() {
    writeToBuildFile(PROPERTY_MODIFIED_TEST_FILE)

    val buildModel = gradleBuildModel
    val storePassword = buildModel.android().signingConfigs()[0]!!.storePassword()
    assertFalse(storePassword.isModified);

    storePassword.setValue("nice")
    assertTrue(storePassword.isModified)

    storePassword.setValue(iStr("KSTOREPWD"))
    assertFalse(storePassword.isModified)

    storePassword.type = PasswordPropertyModel.PasswordType.PLAIN_TEXT
    assertTrue(storePassword.isModified)

    storePassword.type = PasswordPropertyModel.PasswordType.ENVIRONMENT_VARIABLE
    storePassword.setValue(iStr("KSTOREPWD"))
    assertFalse(storePassword.isModified)
  }

  @Test
  fun testIsModifiedDependencies() {
    writeToBuildFile(PROPERTY_MODIFIED_TEST_FILE)

    val buildModel = gradleBuildModel
    val firstDependencyModel = buildModel.dependencies().artifacts()[0]!!
    val secondDependencyModel = buildModel.dependencies().artifacts()[1]!!

    assertFalse(firstDependencyModel.group().isModified)
    assertFalse(firstDependencyModel.classifier().isModified)
    assertFalse(firstDependencyModel.extension().isModified)
    assertFalse(firstDependencyModel.version().isModified)
    assertFalse(firstDependencyModel.completeModel().isModified)
    assertFalse(secondDependencyModel.group().isModified)
    assertFalse(secondDependencyModel.classifier().isModified)
    assertFalse(secondDependencyModel.extension().isModified)
    assertFalse(secondDependencyModel.version().isModified)
    assertFalse(secondDependencyModel.completeModel().isModified)

    firstDependencyModel.version().setValue("45.3.0")

    // Fake properties share the modified state of their parent.
    assertTrue(firstDependencyModel.group().isModified)
    assertTrue(firstDependencyModel.classifier().isModified)
    assertTrue(firstDependencyModel.extension().isModified)
    assertTrue(firstDependencyModel.version().isModified)
    assertTrue(firstDependencyModel.completeModel().isModified)
    assertFalse(secondDependencyModel.group().isModified)
    assertFalse(secondDependencyModel.classifier().isModified)
    assertFalse(secondDependencyModel.extension().isModified)
    assertFalse(secondDependencyModel.version().isModified)
    assertFalse(secondDependencyModel.completeModel().isModified)

    firstDependencyModel.version().setValue("thing")

    assertFalse(firstDependencyModel.group().isModified)
    assertFalse(firstDependencyModel.classifier().isModified)
    assertFalse(firstDependencyModel.extension().isModified)
    assertFalse(firstDependencyModel.version().isModified)
    assertFalse(firstDependencyModel.completeModel().isModified)
    assertFalse(secondDependencyModel.group().isModified)
    assertFalse(secondDependencyModel.classifier().isModified)
    assertFalse(secondDependencyModel.extension().isModified)
    assertFalse(secondDependencyModel.version().isModified)
    assertFalse(secondDependencyModel.completeModel().isModified)
  }

  @Test
  fun testIsModifiedProguardFiles() {
    writeToBuildFile(PROPERTY_MODIFIED_TEST_FILE)

    val buildModel = gradleBuildModel
    val listModel = buildModel.android().defaultConfig().proguardFiles()
    val firstModel = listModel.toList()!![0]
    val secondModel = listModel.toList()!![1]

    assertFalse(firstModel.isModified)
    assertFalse(secondModel.isModified)
    assertFalse(listModel.isModified)

    firstModel.setValue("my_awesome_file.txt")

    assertTrue(firstModel.isModified)
    assertFalse(secondModel.isModified)
    assertTrue(listModel.isModified)

    secondModel.setValue("another_awesome_file.txt")

    assertTrue(firstModel.isModified)
    assertTrue(secondModel.isModified)
    assertTrue(listModel.isModified)

    firstModel.setValue("proguard-android-1.txt")

    assertFalse(firstModel.isModified)
    assertTrue(secondModel.isModified)
    assertTrue(listModel.isModified)

    secondModel.setValue("proguard-rules-1.txt")

    assertFalse(firstModel.isModified)
    assertFalse(secondModel.isModified)
    assertFalse(listModel.isModified)
  }

  @Test
  fun testAccessingNestedBlockElements() {
    writeToBuildFile("")

    val buildModel = gradleBuildModel
    assertNotNull(buildModel.android().defaultConfig())

    verifyFileContents(myBuildFile, "")
  }
}