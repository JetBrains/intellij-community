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

import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_ABOVE_EXT
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_ABOVE_EXT_QUALIFIED_REFERENCE
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_ADD_EXT_BLOCK_AFTER_APPLY
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_ADD_EXT_BLOCK_AFTER_APPLY_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_ADD_EXT_BLOCK_AFTER_MULTIPLE_APPLIES
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_ADD_EXT_BLOCK_AFTER_MULTIPLE_APPLIES_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_ADD_EXT_BLOCK_TO_TOP
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_ADD_EXT_BLOCK_TO_TOP_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_ADD_LIST_DEPENDENCY_WITH_EXISTING_INDEX_REFERENCE
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_ADD_MAP_DEPENDENCY_WITH_EXISTING_KEY_REFERENCE
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_ADD_PROPERTIES_TO_EMPTY_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_ADD_PROPERTY_WITH_EXISTING_DEPENDENCY
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_ADD_PROPERTY_WITH_EXISTING_DEPENDENCY_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_ADD_QUALIFIED_DEPENDENCY_WITH_EXISTING_REFERENCE
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_CHANGE_REFERENCE_VALUE_REORDERS_PROPERTIES
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_CHANGE_REFERENCE_VALUE_REORDERS_PROPERTIES_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_CHANGE_VALUE_TO_OUT_OF_SCOPE_REF
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_CHANGE_VALUE_TO_OUT_OF_SCOPE_REF_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_CREATE_BLOCK_ELEMENT
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_CREATE_BLOCK_ELEMENT_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_CREATE_PROPERTY_MIDDLE
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_CREATE_PROPERTY_MIDDLE_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_CREATE_PROPERTY_START_AND_END
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_CREATE_PROPERTY_START_AND_END_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_CREATE_THEN_MOVE
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_CREATE_THEN_MOVE_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_CREATE_THEN_MOVE_BLOCK_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_CREATE_THEN_MOVE_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_DIRECT_REFERENCE_EXT_CORRECT_ORDER
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_EDIT_BEFORE_MOVE
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_EDIT_BEFORE_MOVE_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_EXT_REFERENCE_TO_VAR
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_MOVE_BASIC_STATEMENT
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_MOVE_BASIC_STATEMENT_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_MOVE_BASIC_WITH_COMMENTS
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_MOVE_BASIC_WITH_COMMENTS_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_MOVE_BEFORE_EDIT
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_MOVE_BEFORE_EDIT_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_MOVE_BLOCK_STATEMENT
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_MOVE_BLOCK_STATEMENT_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_MOVE_BLOCK_STATEMENT_EXPECTED_TWO
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_MOVE_BLOCK_WITH_UNPARSED_CONTENT
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_MOVE_BLOCK_WITH_UNPARSED_CONTENT_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_MOVE_COMMENTS_IN_BLOCK
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_MOVE_COMMENTS_IN_BLOCK_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_RENAME_REORDERS_PROPERTIES
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_RENAME_REORDERS_PROPERTIES_EXPECTED
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_RESOLVE_TO_LAST_PROPERTY
import com.android.tools.idea.gradle.dsl.TestFileName.PROPERTY_ORDER_WRONG_ORDER_NO_DEPENDENCY
import com.android.tools.idea.gradle.dsl.api.GradleFileModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.INTEGER_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BOOLEAN
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.INTEGER
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.REFERENCE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.VARIABLE
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelImpl
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModelImpl
import com.android.tools.idea.gradle.dsl.model.android.SigningConfigModelImpl.STORE_PASSWORD
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test

class PropertyOrderTest : GradleFileModelTestCase() {
  private fun GradleDslModel.dslElement(): GradlePropertiesDslElement {
    assert(this is GradleDslBlockModel)
    val field = GradleDslBlockModel::class.java.getDeclaredField("myDslElement")
    field.isAccessible = true
    return field.get(this) as GradlePropertiesDslElement
  }

  private fun GradleFileModel.dslElement(): GradlePropertiesDslElement {
    assert(this is GradleFileModelImpl)
    val field = GradleFileModelImpl::class.java.getDeclaredField("myGradleDslFile")
    field.isAccessible = true
    return field.get(this) as GradlePropertiesDslElement
  }

  private class TestBlockElement(parent: GradleDslElement, name: String) : GradleDslBlockElement(parent, GradleNameElement.create(name))

  private fun newLiteral(parent: GradleDslElement, name: String, value: Any): GradleDslLiteral {
    val newElement = GradleDslLiteral(parent, GradleNameElement.create(name))
    newElement.setValue(value)
    newElement.setUseAssignment(true)
    newElement.elementType = REGULAR
    return newElement
  }

  private fun newBlock(parent: GradleDslElement, name: String): GradlePropertiesDslElement {
    return TestBlockElement(parent, name)
  }

  @Test
  fun testAddPropertiesToEmpty() {
    assumeTrue(isGroovy())
    writeToBuildFile("")

    val buildModel = gradleBuildModel

    run {
      val firstProperty = buildModel.ext().findProperty("prop1")
      firstProperty.setValue(1)
      val secondProperty = buildModel.ext().findProperty("prop2")
      secondProperty.setValue("2")
      val thirdProperty = buildModel.ext().findProperty("prop3")
      thirdProperty.convertToEmptyList().addListValue().setValue(3)
      val forthProperty = buildModel.ext().findProperty("prop4")
      forthProperty.convertToEmptyMap().getMapValue("key").setValue("4")
    }

    fun verify() {
      val firstProperty = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstProperty, INTEGER_TYPE, 1, INTEGER, REGULAR, 0, "prop1")
      val secondProperty = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondProperty, STRING_TYPE, "2", STRING, REGULAR, 0, "prop2")
      val thirdProperty = buildModel.ext().findProperty("prop3")
      verifyListProperty(thirdProperty, listOf(3), REGULAR, 0, "prop3")
      val forthProperty = buildModel.ext().findProperty("prop4")
      verifyMapProperty(forthProperty, mapOf("key" to "4"), REGULAR, 0, "prop4")
    }

    verify()

    applyChangesAndReparse(buildModel)

    verifyFileContents(myBuildFile, PROPERTY_ORDER_ADD_PROPERTIES_TO_EMPTY_EXPECTED)
    verify()
  }

  @Test
  fun testCreatePropertyMiddle() {
    writeToBuildFile(PROPERTY_ORDER_CREATE_PROPERTY_MIDDLE)

    val buildModel = gradleBuildModel

    val dslElement = buildModel.ext().dslElement()
    dslElement.addNewElementAt(1, newLiteral(dslElement, "prop2", 2))

    fun verify() {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstModel, INTEGER_TYPE, 1, INTEGER, REGULAR, 0, "prop1")
      val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, INTEGER_TYPE, 2, INTEGER, REGULAR, 0, "prop2")
      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(thirdModel, INTEGER_TYPE, 3, INTEGER, REGULAR, 0, "prop3")
    }

    verify()

    applyChangesAndReparse(buildModel)

    verifyFileContents(myBuildFile, PROPERTY_ORDER_CREATE_PROPERTY_MIDDLE_EXPECTED)
    verify()
  }

  @Test
  fun testCreatePropertyStartAndEnd() {
    writeToBuildFile(PROPERTY_ORDER_CREATE_PROPERTY_START_AND_END)

    val buildModel = gradleBuildModel

    val dslElement = buildModel.ext().dslElement()
    dslElement.addNewElementAt(0, newLiteral(dslElement, "prop1", "1"))
    dslElement.addNewElementAt(4, newLiteral(dslElement, "prop4", 4))

    fun verify() {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstModel, STRING_TYPE, "1", STRING, REGULAR, 0, "prop1")
      val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, INTEGER_TYPE, 2, INTEGER, REGULAR, 0, "prop2")
      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(thirdModel, INTEGER_TYPE, 3, INTEGER, REGULAR, 0, "prop3")
      val forthModel = buildModel.ext().findProperty("prop4")
      verifyPropertyModel(forthModel, INTEGER_TYPE, 4, INTEGER, REGULAR, 0, "prop4")
    }

    verify()

    applyChangesAndReparse(buildModel)

    verifyFileContents(myBuildFile, PROPERTY_ORDER_CREATE_PROPERTY_START_AND_END_EXPECTED)
    verify()
  }

  @Test
  fun testCreateBlockElement() {
    writeToBuildFile(PROPERTY_ORDER_CREATE_BLOCK_ELEMENT)

    val buildModel = gradleBuildModel
    val dslElement = buildModel.ext().dslElement()
    val topBlock = newBlock(dslElement, "topBlock")
    // Adding at an index that doesn't exist should just place the element at the end.
    topBlock.addNewElementAt(400000, newLiteral(topBlock, "prop2", true))
    topBlock.addNewElementAt(1, newLiteral(topBlock, "prop3", false))
    topBlock.addNewElementAt(0, newLiteral(topBlock, "prop1", 42))

    val bottomBlock = newBlock(dslElement, "bottomBlock")
    // Using a negative index will add the element to the start of the list.
    bottomBlock.addNewElementAt(-1, newLiteral(bottomBlock, "prop4", "hello"))
    bottomBlock.addNewElementAt(1, newLiteral(bottomBlock, "prop6", false))
    bottomBlock.addNewElementAt(1, newLiteral(bottomBlock, "prop5", true))

    val middleBlock = newBlock(dslElement, "middleBlock")
    middleBlock.addNewElementAt(0, newLiteral(middleBlock, "greeting", "goodbye :)"))

    dslElement.addNewElementAt(0, topBlock)
    dslElement.addNewElementAt(2, bottomBlock)
    dslElement.addNewElementAt(1, middleBlock)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PROPERTY_ORDER_CREATE_BLOCK_ELEMENT_EXPECTED)
  }

  @Test
  fun testMoveBlockStatement() {
    writeToBuildFile(PROPERTY_ORDER_MOVE_BLOCK_STATEMENT)

    val buildModel = gradleBuildModel
    val extElement = buildModel.ext().dslElement()
    val fileElement = buildModel.dslElement()

    fileElement.moveElementTo(0, extElement)

    applyChangesAndReparse(buildModel)

    verifyFileContents(myBuildFile, PROPERTY_ORDER_MOVE_BLOCK_STATEMENT_EXPECTED)

    // Check that the build model still works.
    val ext = buildModel.ext()
    val extProperties = ext.declaredProperties
    assertSize(2, extProperties)
    verifyPropertyModel(extProperties[0], STRING_TYPE, "hello", STRING, REGULAR, 0, "prop1")
    verifyPropertyModel(extProperties[1], BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "prop2")
    val debug = buildModel.android().buildTypes()[0]!!
    val debugProperties = debug.declaredProperties
    assertSize(1, debugProperties)
    verifyPropertyModel(debugProperties[0], STRING_TYPE, "sneaky", REFERENCE, VARIABLE, 0, "var")

    // Delete one and edit one to make sure the PsiElements are still valid for these operations.
    extProperties[0].delete()
    extProperties[1].setValue(43)
    extProperties[1].rename("prop")

    val newExtProperties = ext.declaredProperties
    assertSize(1, newExtProperties)
    verifyPropertyModel(newExtProperties[0], INTEGER_TYPE, 43, INTEGER, REGULAR, 0, "prop")

    applyChangesAndReparse(buildModel)

    // Check the resulting file again.
    verifyFileContents(myBuildFile, PROPERTY_ORDER_MOVE_BLOCK_STATEMENT_EXPECTED_TWO)
  }

  @Test
  fun testMoveBasicStatement() {
    writeToBuildFile(PROPERTY_ORDER_MOVE_BASIC_STATEMENT)

    val buildModel = gradleBuildModel
    val extElement = buildModel.ext().dslElement()
    val fileElement = buildModel.dslElement()
    val extElementMap = extElement.elements

    // Reorder the elements inside the ext block.
    extElement.moveElementTo(0, extElementMap["prop2"]!!)
    extElement.moveElementTo(2, extElementMap["prop1"]!!)
    extElement.moveElementTo(extElementMap.size, extElementMap["var3"]!!)

    // Move the Ext block to the bottom of the file.
    fileElement.moveElementTo(Int.MAX_VALUE, extElement)

    fun verify() {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstModel, STRING_TYPE, "3", STRING, REGULAR, 0, "prop1")
      val firstVarModel = buildModel.ext().findProperty("var1")
      verifyPropertyModel(firstVarModel, INTEGER_TYPE, 2, INTEGER, VARIABLE, 0, "var1")
      val secondModel = buildModel.ext().findProperty("prop2")
      verifyListProperty(secondModel, listOf(1, true, "1"), REGULAR, 0, "prop2")
      val secondVarModel = buildModel.ext().findProperty("var2")
      verifyPropertyModel(secondVarModel, STRING_TYPE, "var1", REFERENCE, VARIABLE, 1, "var2")
      val thirdVarModel = buildModel.ext().findProperty("var3")
      verifyMapProperty(thirdVarModel, mapOf("key" to 6, "key1" to "7"), VARIABLE, 0, "var3")
    }

    verify()

    applyChangesAndReparse(buildModel)

    verifyFileContents(myBuildFile, PROPERTY_ORDER_MOVE_BASIC_STATEMENT_EXPECTED)
    verify()
  }

  @Test
  fun testCreateThenMoveBlock() {
    writeToBuildFile(PROPERTY_ORDER_CREATE_THEN_MOVE_BLOCK)

    val buildModel = gradleBuildModel

    // Create a property, this will create an ext element.
    val propertyModel = buildModel.ext().findProperty("prop1")
    propertyModel.setValue(true)

    // Then move it to the top of the file.
    val extElement = buildModel.ext().dslElement()
    val fileElement = buildModel.dslElement()
    fileElement.moveElementTo(0, extElement)

    applyChangesAndReparse(buildModel)

    verifyFileContents(myBuildFile, PROPERTY_ORDER_CREATE_THEN_MOVE_BLOCK_EXPECTED)
  }

  @Test
  fun testCreateThenMove() {
    writeToBuildFile(PROPERTY_ORDER_CREATE_THEN_MOVE)

    val buildModel = gradleBuildModel
    val extElement = buildModel.ext().dslElement()

    // Create three properties.
    val firstModel = buildModel.ext().findProperty("prop1")
    firstModel.convertToEmptyMap()
    firstModel.getMapValue("key").setValue(1)
    firstModel.getMapValue("key1").setValue("two")
    val secondModel = buildModel.ext().findProperty("prop2")
    secondModel.setValue(72)

    // Move them around
    val elementMap = extElement.elements
    extElement.moveElementTo(0, elementMap["prop2"]!!)
    // Note: Even though this is effectively a no-op, a move of the property still occurs.
    extElement.moveElementTo(1, elementMap["prop1"]!!)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PROPERTY_ORDER_CREATE_THEN_MOVE_EXPECTED)
  }

  @Test
  fun testEditBeforeMove() {
    writeToBuildFile(PROPERTY_ORDER_EDIT_BEFORE_MOVE)

    val buildModel = gradleBuildModel

    val extElement = buildModel.ext().dslElement()

    // Edit the elements
    val firstModel = buildModel.ext().findProperty("prop1")
    firstModel.convertToEmptyList().addListValue().setValue("one")
    firstModel.addListValue().setValue("two")
    firstModel.addListValue().setValue("three")
    val secondModel = buildModel.ext().findProperty("prop3")
    secondModel.setValue(true)

    // We need to get the map after since editing the elements will cause it to change.
    val extElementMap = extElement.elements

    // Swap prop1 and prop3
    extElement.moveElementTo(3, extElementMap["prop1"]!!)
    extElement.moveElementTo(0, extElementMap["prop3"]!!)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PROPERTY_ORDER_EDIT_BEFORE_MOVE_EXPECTED)
  }

  @Test
  fun testMoveBeforeEdit() {
    writeToBuildFile(PROPERTY_ORDER_MOVE_BEFORE_EDIT)

    val buildModel = gradleBuildModel

    val extElement = buildModel.ext().dslElement()
    val extElementMap = extElement.elements

    // Swap prop1 and prop3
    extElement.moveElementTo(3, extElementMap["prop1"]!!)
    extElement.moveElementTo(0, extElementMap["prop3"]!!)

    // Edit the elements
    val firstModel = buildModel.ext().findProperty("prop1")
    firstModel.convertToEmptyList().addListValue().setValue("one")
    firstModel.addListValue().setValue("two")
    firstModel.addListValue().setValue("three")
    val secondModel = buildModel.ext().findProperty("prop3")
    secondModel.setValue(true)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PROPERTY_ORDER_MOVE_BEFORE_EDIT_EXPECTED)
  }

  @Test
  fun testMoveCommentsInBlock() {
    writeToBuildFile(PROPERTY_ORDER_MOVE_COMMENTS_IN_BLOCK)

    val buildModel = gradleBuildModel

    val fileElement = buildModel.dslElement()
    val extElement = buildModel.ext().dslElement()

    // Move the ext block to the top
    fileElement.moveElementTo(1, extElement)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PROPERTY_ORDER_MOVE_COMMENTS_IN_BLOCK_EXPECTED)
  }

  @Ignore("Comments don't get moved with the line they are on")
  @Test
  fun testMoveBasicWithComments() {
    writeToBuildFile(PROPERTY_ORDER_MOVE_BASIC_WITH_COMMENTS)

    val buildModel = gradleBuildModel
    val extElement = buildModel.ext().dslElement()

    val extElementMap = extElement.elements

    // Move all of the elements around
    extElement.moveElementTo(0, extElementMap["prop3"]!!)
    extElement.moveElementTo(2, extElementMap["prop1"]!!)
    extElement.moveElementTo(0, extElementMap["prop2"]!!)

    fun verify() {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstModel, STRING_TYPE, "value1", REFERENCE, REGULAR, 0, "prop1")
      val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, STRING_TYPE, "hello", STRING, REGULAR, 0, "prop2")
      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(thirdModel, STRING_TYPE, "Boo", STRING, REGULAR, 0, "prop3")
    }

    verify()

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PROPERTY_ORDER_MOVE_BASIC_WITH_COMMENTS_EXPECTED)
    verify()
  }

  @Test
  fun testMoveBlockWithUnparsedContent() {
    writeToBuildFile(PROPERTY_ORDER_MOVE_BLOCK_WITH_UNPARSED_CONTENT)

    val buildModel = gradleBuildModel
    val extElement = buildModel.ext().dslElement()
    val fileElement = buildModel.dslElement()

    val extElementMap = extElement.elements

    // Move the block containing the unknown elements.
    fileElement.moveElementTo(1, extElement)

    // Move the normal one to the middle of the unknown properties.
    extElement.moveElementTo(2, extElementMap["prop4"]!!)
    // Move some unknown properties around.
    extElement.moveElementTo(0, extElementMap["prop2"]!!)
    extElement.moveElementTo(4, extElementMap["var"]!!)

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, PROPERTY_ORDER_MOVE_BLOCK_WITH_UNPARSED_CONTENT_EXPECTED)
  }

  @Test
  fun testWrongOrderNoDependency() {
    writeToBuildFile(PROPERTY_ORDER_WRONG_ORDER_NO_DEPENDENCY)

    val buildModel = gradleBuildModel
    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstModel, STRING_TYPE, "prop2", REFERENCE, REGULAR, 0, "prop1")
      val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, STRING_TYPE, "hello", STRING, REGULAR, 0, "prop2")
    }
  }

  @Test
  fun testDirectReferenceExtCorrectOrder() {
    writeToBuildFile(PROPERTY_ORDER_DIRECT_REFERENCE_EXT_CORRECT_ORDER)

    val buildModel = gradleBuildModel
    val propertyModel = buildModel.android().defaultConfig().minSdkVersion()
    verifyPropertyModel(propertyModel, INTEGER_TYPE, 20, INTEGER, REGULAR, 1, ProductFlavorModelImpl.MIN_SDK_VERSION)
  }

  @Test
  fun testAboveExt() {
    writeToBuildFile(PROPERTY_ORDER_ABOVE_EXT)

    val buildModel = gradleBuildModel

    val minSdkModel = buildModel.android().defaultConfig().minSdkVersion()
    val maxSdkModel = buildModel.android().defaultConfig().maxSdkVersion()

    verifyPropertyModel(minSdkModel, STRING_TYPE, extraName("minSdk"), REFERENCE, REGULAR, 0, ProductFlavorModelImpl.MIN_SDK_VERSION)
    verifyPropertyModel(maxSdkModel, STRING_TYPE, extraName("maxSdk"), REFERENCE, REGULAR, 0, ProductFlavorModelImpl.MAX_SDK_VERSION)
  }

  @Test
  fun testAboveExtQualifiedReference() {
    writeToBuildFile(PROPERTY_ORDER_ABOVE_EXT_QUALIFIED_REFERENCE)

    val buildModel = gradleBuildModel

    val minSdkModel = buildModel.android().defaultConfig().minSdkVersion()
    val maxSdkModel = buildModel.android().defaultConfig().maxSdkVersion()

    verifyPropertyModel(minSdkModel, STRING_TYPE, "ext.minSdk", REFERENCE, REGULAR, 0, ProductFlavorModelImpl.MIN_SDK_VERSION)
    verifyPropertyModel(maxSdkModel, STRING_TYPE, "ext.maxSdk", REFERENCE, REGULAR, 0, ProductFlavorModelImpl.MAX_SDK_VERSION)
  }

  @Test
  fun testResolveToLastProperty() {
    writeToBuildFile(PROPERTY_ORDER_RESOLVE_TO_LAST_PROPERTY)

    val buildModel = gradleBuildModel
    val configModel = buildModel.android().signingConfigs()[0]!!
    val fileModel = configModel.storeFile()
    val passwordModel = configModel.storePassword()

    verifyPropertyModel(fileModel, STRING_TYPE, "goodbye", STRING, DERIVED, 1, "0")
    verifyPropertyModel(passwordModel.resolve(), STRING_TYPE, "off", STRING, REGULAR, 1, STORE_PASSWORD)
  }

  @Test
  fun testAddPropertyWithExistingDependency() {
    writeToBuildFile(PROPERTY_ORDER_ADD_PROPERTY_WITH_EXISTING_DEPENDENCY)

    val buildModel = gradleBuildModel

    val extModel = buildModel.ext()

    run {
      val propertyModel = extModel.findProperty("prop1")
      val beforePropModel = extModel.findProperty("prop")
      val afterPropModel = extModel.findProperty("prop2")

      propertyModel.setValue(ReferenceTo("prop"))

      verifyPropertyModel(propertyModel.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 1, "prop1")
      verifyPropertyModel(beforePropModel.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 0, "prop")
      verifyPropertyModel(afterPropModel.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 1, "prop2")
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = extModel.findProperty("prop1")
      val beforePropModel = extModel.findProperty("prop")
      val afterPropModel = extModel.findProperty("prop2")

      verifyPropertyModel(propertyModel.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 1, "prop1")
      verifyPropertyModel(beforePropModel.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 0, "prop")
      verifyPropertyModel(afterPropModel.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 1, "prop2")
    }

    verifyFileContents(myBuildFile, PROPERTY_ORDER_ADD_PROPERTY_WITH_EXISTING_DEPENDENCY_EXPECTED)
  }

  @Test
  fun testChangeValueToOutOfScopeRef() {
    writeToBuildFile(PROPERTY_ORDER_CHANGE_VALUE_TO_OUT_OF_SCOPE_REF)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    run {
      val firstPropertyModel = extModel.findProperty("prop")
      val secondPropertyModel = extModel.findProperty("prop1")
      val thirdPropertyModel = extModel.findProperty("prop2")

      secondPropertyModel.setValue(ReferenceTo("prop"))

      verifyPropertyModel(firstPropertyModel.resolve(), INTEGER_TYPE, 24, INTEGER, REGULAR, 0, "prop")
      verifyPropertyModel(secondPropertyModel.resolve(), INTEGER_TYPE, 24, INTEGER, REGULAR, 1, "prop1")
      verifyPropertyModel(thirdPropertyModel.resolve(), INTEGER_TYPE, 24, INTEGER, REGULAR, 1, "prop2")
    }

    applyChangesAndReparse(buildModel)

    run {
      val firstPropertyModel = extModel.findProperty("prop")
      val secondPropertyModel = extModel.findProperty("prop1")
      val thirdPropertyModel = extModel.findProperty("prop2")

      verifyPropertyModel(firstPropertyModel.resolve(), INTEGER_TYPE, 24, INTEGER, REGULAR, 0, "prop")
      verifyPropertyModel(secondPropertyModel.resolve(), INTEGER_TYPE, 24, INTEGER, REGULAR, 1, "prop1")
      verifyPropertyModel(thirdPropertyModel.resolve(), INTEGER_TYPE, 24, INTEGER, REGULAR, 1, "prop2")
    }

    verifyFileContents(myBuildFile, PROPERTY_ORDER_CHANGE_VALUE_TO_OUT_OF_SCOPE_REF_EXPECTED)
  }

  @Test
  fun testRenameReordersProperties() {
    writeToBuildFile(PROPERTY_ORDER_RENAME_REORDERS_PROPERTIES)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    run {
      val firstPropertyModel = extModel.findProperty("oddOneOut")
      val secondPropertyModel = extModel.findProperty("prop1")
      val thirdPropertyModel = extModel.findProperty("prop2")

      firstPropertyModel.rename("prop")
      verifyPropertyModel(firstPropertyModel.resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "prop")
      verifyPropertyModel(secondPropertyModel.resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 1, "prop1")
      verifyPropertyModel(thirdPropertyModel.resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 1, "prop2")
    }

    applyChangesAndReparse(buildModel)

    run {
      val firstPropertyModel = extModel.findProperty("prop")
      val secondPropertyModel = extModel.findProperty("prop1")
      val thirdPropertyModel = extModel.findProperty("prop2")

      verifyPropertyModel(firstPropertyModel.resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "prop")
      verifyPropertyModel(secondPropertyModel.resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 1, "prop1")
      verifyPropertyModel(thirdPropertyModel.resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 1, "prop2")
    }

    verifyFileContents(myBuildFile, PROPERTY_ORDER_RENAME_REORDERS_PROPERTIES_EXPECTED)
  }

  @Test
  fun testChangeReferenceValueReordersProperties() {
    writeToBuildFile(PROPERTY_ORDER_CHANGE_REFERENCE_VALUE_REORDERS_PROPERTIES)

    val buildModel = gradleBuildModel

    run {
      val extModel = buildModel.ext()
      val firstPropertyModel = extModel.findProperty("prop")
      val secondPropertyModel = extModel.findProperty("prop1")
      val thirdPropertyModel = extModel.findProperty("prop2")
      val fourthPropertyModel = extModel.findProperty("prop3")
      val fifthPropertyModel = extModel.findProperty("prop4")
      val sixthPropertyModel = extModel.findProperty("prop5")
      val seventhPropertyModel = extModel.findProperty("prop6")

      firstPropertyModel.setValue("hello")
      thirdPropertyModel.setValue("goodbye")
      seventhPropertyModel.setValue(ReferenceTo("prop5"))

      verifyPropertyModel(firstPropertyModel.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 0, "prop")
      verifyPropertyModel(secondPropertyModel.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 1, "prop1")
      verifyPropertyModel(thirdPropertyModel.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 0, "prop2")
      verifyPropertyModel(fourthPropertyModel.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "prop3")
      verifyPropertyModel(fifthPropertyModel.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "prop4")
      verifyPropertyModel(sixthPropertyModel.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "prop5")
      verifyPropertyModel(seventhPropertyModel.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "prop6")
    }

    applyChangesAndReparse(buildModel)

    run {
      val extModel = buildModel.ext()
      val firstPropertyModel = extModel.findProperty("prop")
      val secondPropertyModel = extModel.findProperty("prop1")
      val thirdPropertyModel = extModel.findProperty("prop2")
      val fourthPropertyModel = extModel.findProperty("prop3")
      val fifthPropertyModel = extModel.findProperty("prop4")
      val sixthPropertyModel = extModel.findProperty("prop5")
      val seventhPropertyModel = extModel.findProperty("prop6")

      verifyPropertyModel(firstPropertyModel.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 0, "prop")
      verifyPropertyModel(secondPropertyModel.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 1, "prop1")
      verifyPropertyModel(thirdPropertyModel.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 0, "prop2")
      verifyPropertyModel(fourthPropertyModel.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "prop3")
      verifyPropertyModel(fifthPropertyModel.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "prop4")
      verifyPropertyModel(sixthPropertyModel.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "prop5")
      verifyPropertyModel(seventhPropertyModel.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "prop6")
    }

    verifyFileContents(myBuildFile, PROPERTY_ORDER_CHANGE_REFERENCE_VALUE_REORDERS_PROPERTIES_EXPECTED)
  }

  @Test
  fun testAddListDependencyWithExistingIndexReference() {
    writeToBuildFile(PROPERTY_ORDER_ADD_LIST_DEPENDENCY_WITH_EXISTING_INDEX_REFERENCE)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()
    val newListModel = extModel.findProperty("prop")
    newListModel.convertToEmptyList().addListValue().setValue("hello")

    verifyPropertyModel(extModel.findProperty("prop1").resolve(), STRING_TYPE, "hello", STRING, REGULAR, 1, "prop1")

    applyChangesAndReparse(buildModel)

    verifyPropertyModel(buildModel.ext().findProperty("prop1").resolve(), STRING_TYPE, "hello", STRING, REGULAR, 1, "prop1")
  }

  @Test
  fun testAddMapDependencyWithExistingKeyReference() {
    writeToBuildFile(PROPERTY_ORDER_ADD_MAP_DEPENDENCY_WITH_EXISTING_KEY_REFERENCE)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()
    val newMapModel = extModel.findProperty("prop")
    newMapModel.convertToEmptyMap().getMapValue("key").setValue(true)

    verifyPropertyModel(extModel.findProperty("prop1").resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 1, "prop1")

    applyChangesAndReparse(buildModel)

    verifyPropertyModel(buildModel.ext().findProperty("prop1").resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 1, "prop1")
  }

  @Test
  fun testAddQualifiedDependencyWithExistingReference() {
    writeToBuildFile(PROPERTY_ORDER_ADD_QUALIFIED_DEPENDENCY_WITH_EXISTING_REFERENCE)

    val buildModel = gradleBuildModel
    val newPropertyModel = buildModel.ext().findProperty("prop")
    newPropertyModel.setValue("boo")

    verifyPropertyModel(buildModel.ext().findProperty("prop1").resolve(), STRING_TYPE, "boo", STRING, REGULAR, 1, "prop1")

    applyChangesAndReparse(buildModel)

    verifyPropertyModel(buildModel.ext().findProperty("prop1").resolve(), STRING_TYPE, "boo", STRING, REGULAR, 1, "prop1")
  }

  @Test
  fun testAddExtBlockAfterApply() {
    writeToBuildFile(PROPERTY_ORDER_ADD_EXT_BLOCK_AFTER_APPLY)

    val buildModel = gradleBuildModel

    buildModel.ext().findProperty("newProp").setValue(true)

    applyChangesAndReparse(buildModel)

    verifyFileContents(myBuildFile, PROPERTY_ORDER_ADD_EXT_BLOCK_AFTER_APPLY_EXPECTED)
  }

  @Test
  fun testAddExtBlockAfterMultipleApplies() {
    writeToBuildFile(PROPERTY_ORDER_ADD_EXT_BLOCK_AFTER_MULTIPLE_APPLIES)

    val buildModel = gradleBuildModel

    buildModel.ext().findProperty("newProp").setValue(true)

    applyChangesAndReparse(buildModel)

    verifyFileContents(myBuildFile, PROPERTY_ORDER_ADD_EXT_BLOCK_AFTER_MULTIPLE_APPLIES_EXPECTED)
  }

  @Test
  fun testAddExtBlockToTop() {
    writeToBuildFile(PROPERTY_ORDER_ADD_EXT_BLOCK_TO_TOP)

    val buildModel = gradleBuildModel

    buildModel.ext().findProperty("newProp").setValue(true)

    applyChangesAndReparse(buildModel)

    verifyFileContents(myBuildFile, PROPERTY_ORDER_ADD_EXT_BLOCK_TO_TOP_EXPECTED)
  }

  @Test
  fun testExtReferenceToVar() {
    writeToBuildFile(PROPERTY_ORDER_EXT_REFERENCE_TO_VAR)

    val buildModel = gradleBuildModel

    val propertyModel = buildModel.ext().findProperty("value")
    verifyPropertyModel(propertyModel.resolve(), STRING_TYPE, "com.android.support:appcompat-v7:1.0", STRING, REGULAR, 1)
  }
}