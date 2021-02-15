// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.dsl.model.ext

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.*
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.*
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl
import com.android.tools.idea.gradle.dsl.model.notifications.CircularApplication
import com.google.common.collect.ImmutableMap
import com.intellij.testFramework.UsefulTestCase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.annotations.SystemDependent
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal

class GradlePropertyModelTest : GradleFileModelTestCase() {
  @Test
  fun testPropertiesFromScratch() {
    writeToBuildFile(TestFile.PROPERTIES_FROM_SCRATCH)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    extModel.findProperty("newProp").setValue(123)
    extModel.findProperty("prop1").setValue(ReferenceTo("newProp"))

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.PROPERTIES_FROM_SCRATCH_EXPECTED)
  }

  @Test
  fun testPropertiesFromScratchArrayExpression() {
    writeToBuildFile(TestFile.PROPERTIES_FROM_SCRATCH_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel
    var extModel = buildModel.ext()

    extModel.findProperty("ext.newProp").setValue(123)
    extModel.findProperty("ext.prop1").setValue(ReferenceTo("newProp"))
    extModel.findProperty("prop2").setValue(ReferenceTo("ext.newProp"))

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.PROPERTIES_FROM_SCRATCH_ARRAY_EXPRESSION_EXPECTED)

    extModel = buildModel.ext()
    assertThat(extModel.findProperty("newProp").getValue(INTEGER_TYPE), equalTo(123))
    assertThat(extModel.findProperty("prop1").resolve().getValue(INTEGER_TYPE), equalTo(123))
    assertThat(extModel.findProperty("prop2").resolve().getValue(INTEGER_TYPE), equalTo(123))
  }

  @Test
  fun testProperties() {
    writeToBuildFile(TestFile.PROPERTIES)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    run {
      val propertyModel = extModel.findProperty("prop1")
      assertEquals(STRING, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertEquals("value", propertyModel.getValue(STRING_TYPE))
      assertEquals("value", propertyModel.getRawValue(STRING_TYPE))
      assertEquals("prop1", propertyModel.name)
      assertEquals("ext.prop1", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop2")
      assertEquals(INTEGER, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertEquals(25, propertyModel.getValue(INTEGER_TYPE))
      assertEquals(25, propertyModel.getRawValue(INTEGER_TYPE))
      assertEquals("prop2", propertyModel.name)
      assertEquals("ext.prop2", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop3")
      assertEquals(BOOLEAN, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertEquals(true, propertyModel.getValue(BOOLEAN_TYPE))
      assertEquals(true, propertyModel.getRawValue(BOOLEAN_TYPE))
      assertEquals("prop3", propertyModel.name)
      assertEquals("ext.prop3", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop4")
      assertEquals(MAP, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertEquals("prop4", propertyModel.name)
      assertEquals("ext.prop4", propertyModel.fullyQualifiedName)

      val value = propertyModel.getValue(MAP_TYPE)!!["key"]!!
      assertEquals("val", value.getValue(STRING_TYPE))
      assertEquals(DERIVED, value.propertyType)
      assertEquals(STRING, value.valueType)
      assertEquals("key", value.name)
      assertEquals("ext.prop4.key", value.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop5")
      assertEquals(LIST, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertEquals("prop5", propertyModel.name)
      assertEquals("ext.prop5", propertyModel.fullyQualifiedName)
      val list = propertyModel.getValue(LIST_TYPE)!!
      assertSize(3, list)

      run {
        val value = list[0]
        assertEquals("val1", value.getValue(STRING_TYPE))
        assertEquals(DERIVED, value.propertyType)
        assertEquals(STRING, value.valueType)
      }

      run {
        val value = list[1]
        assertEquals("val2", value.getValue(STRING_TYPE))
        assertEquals(DERIVED, value.propertyType)
        assertEquals(STRING, value.valueType)
      }

      run {
        val value = list[2]
        assertEquals("val3", value.getValue(STRING_TYPE))
        assertEquals(DERIVED, value.propertyType)
        assertEquals(STRING, value.valueType)
      }
    }

    run {
      val propertyModel = extModel.findProperty("prop6")
      assertEquals(BigDecimal("25.3"), propertyModel.toBigDecimal())
      assertEquals(BIG_DECIMAL, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      assertEquals("prop6", propertyModel.name)
      assertEquals("ext.prop6", propertyModel.fullyQualifiedName)
    }
  }

  @Test
  fun testVariables() {
    writeToBuildFile(TestFile.VARIABLES)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    fun findVariable(name: String): GradlePropertyModel {
      return when {
        isGroovy -> extModel.findProperty(name)
        else -> buildModel.declaredProperties.find { it.name == name }!!
      }
    }

    fun assertMaybeExtFQN(expected: String, actual: String) {
      when {
        isGroovy -> assertEquals("ext.${expected}", actual)
        else -> assertEquals(expected, actual)
      }
    }

    run {
      val propertyModel = findVariable("prop1")
      assertEquals(STRING, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals("value", propertyModel.getValue(STRING_TYPE))
      assertEquals("value", propertyModel.getRawValue(STRING_TYPE))
      assertEquals("prop1", propertyModel.name)
      assertMaybeExtFQN("prop1", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = findVariable("prop2")
      assertEquals(INTEGER, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals(25, propertyModel.getValue(INTEGER_TYPE))
      assertEquals(25, propertyModel.getRawValue(INTEGER_TYPE))
      assertEquals("prop2", propertyModel.name)
      assertMaybeExtFQN("prop2", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = findVariable("prop3")
      assertEquals(BOOLEAN, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals(true, propertyModel.getValue(BOOLEAN_TYPE))
      assertEquals(true, propertyModel.getRawValue(BOOLEAN_TYPE))
      assertEquals("prop3", propertyModel.name)
      assertMaybeExtFQN("prop3", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = findVariable("prop4")
      assertEquals(MAP, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals("prop4", propertyModel.name)
      assertMaybeExtFQN("prop4", propertyModel.fullyQualifiedName)
      val value = propertyModel.getValue(MAP_TYPE)!!["key"]!!
      assertEquals("val", value.getValue(STRING_TYPE))
      assertEquals(DERIVED, value.propertyType)
      assertEquals(STRING, value.valueType)
    }

    run {
      val propertyModel = findVariable("prop5")
      assertEquals(LIST, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals("prop5", propertyModel.name)
      assertMaybeExtFQN("prop5", propertyModel.fullyQualifiedName)
      val list = propertyModel.getValue(LIST_TYPE)!!
      assertSize(3, list)

      run {
        val value = list[0]
        assertEquals("val1", value.getValue(STRING_TYPE))
        assertEquals(DERIVED, value.propertyType)
        assertEquals(STRING, value.valueType)
      }

      run {
        val value = list[1]
        assertEquals("val2", value.getValue(STRING_TYPE))
        assertEquals(DERIVED, value.propertyType)
        assertEquals(STRING, value.valueType)
      }

      run {
        val value = list[2]
        assertEquals("val3", value.getValue(STRING_TYPE))
        assertEquals(DERIVED, value.propertyType)
        assertEquals(STRING, value.valueType)
      }
    }

    run {
      val propertyModel = findVariable("prop6")
      assertEquals(BigDecimal("25.3"), propertyModel.toBigDecimal())
      assertEquals(BIG_DECIMAL, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals("prop6", propertyModel.name)
      assertMaybeExtFQN("prop6", propertyModel.fullyQualifiedName)
    }
  }

  @Test
  fun testUnknownValues() {
    writeToBuildFile(TestFile.UNKNOWN_VALUES)

    val buildModel = gradleBuildModel

    run {
      val propertyOne = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyOne, STRING_TYPE, "z(1)", UNKNOWN, REGULAR, 0)
      val propertyTwo = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyTwo, STRING_TYPE, "1 + 2", UNKNOWN, REGULAR, 0)
      val propertyThree = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(propertyThree, STRING_TYPE, "obj.getName()", UNKNOWN, REGULAR, 0)
    }
  }

  @Test
  fun testUnknownValuesInMap() {
    writeToBuildFile(TestFile.UNKNOWN_VALUES_IN_MAP)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(3, map.entries)
      verifyPropertyModel(map["key"], STRING_TYPE, "getValue()", UNKNOWN, DERIVED, 0)
      verifyPropertyModel(map["key2"], STRING_TYPE, "2 + 3", UNKNOWN, DERIVED, 0)
      verifyPropertyModel(map["key3"], STRING_TYPE, "z(1)", UNKNOWN, DERIVED, 0)
    }
  }

  @Test
  fun testUnknownValuesInList() {
    writeToBuildFile(TestFile.UNKNOWN_VALUES_IN_LIST)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(LIST, propertyModel.valueType)
      val list = propertyModel.getValue(LIST_TYPE)!!
      assertSize(3, list)
      verifyPropertyModel(list[0], STRING_TYPE, "getValue()", UNKNOWN, DERIVED, 0)
      verifyPropertyModel(list[1], STRING_TYPE, "2 + 3", UNKNOWN, DERIVED, 0)
      verifyPropertyModel(list[2], STRING_TYPE, "z(1)", UNKNOWN, DERIVED, 0)
    }
  }

  @Test
  fun testGetProperties() {
    writeToBuildFile(TestFile.GET_PROPERTIES)

    val extModel = gradleBuildModel.ext()
    val properties = extModel.properties
    // Note: this shouldn't include variables.
    assertSize(3, properties)

    verifyPropertyModel(properties[0], STRING_TYPE, "var1", REFERENCE, REGULAR, 1, "prop1", "ext.prop1")
    verifyPropertyModel(properties[0].dependencies[0], STRING_TYPE, "Value1", STRING, VARIABLE, 0, "var1")
    verifyPropertyModel(properties[1], STRING_TYPE, "Cool Value2", STRING, REGULAR, 1, "prop2", "ext.prop2")
    verifyPropertyModel(properties[1].dependencies[0], STRING_TYPE, "Value2", STRING, VARIABLE, 0, "var2")
    verifyPropertyModel(properties[2], STRING_TYPE, "Nice Value3", STRING, REGULAR, 1, "prop3", "ext.prop3")
    verifyPropertyModel(properties[2].dependencies[0], STRING_TYPE, "Value3", STRING, VARIABLE, 0, "var3")
  }

  @Test
  fun testGetVariables() {
    assumeTrue("no ext block in KotlinScript", !isKotlinScript) // TODO(b/154902406)
    writeToBuildFile(TestFile.GET_VARIABLES)

    val extModel = gradleBuildModel.ext()
    val variables = extModel.variables
    // Note: this shouldn't include properties.
    assertSize(3, variables)

    verifyPropertyModel(variables[0], STRING_TYPE, "gecko", STRING, VARIABLE, 0, "var1", "ext.var1")
    verifyPropertyModel(variables[1], STRING_TYPE, "barbet", STRING, VARIABLE, 0, "var2", "ext.var2")
    verifyPropertyModel(variables[2], STRING_TYPE, "crane", STRING, VARIABLE, 0, "var3", "ext.var3")
  }

  @Test
  fun testAsType() {
    writeToBuildFile(TestFile.AS_TYPE)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    fun findProperty(name: String): GradlePropertyModel {
      return when {
        isGroovy -> extModel.findProperty(name)
        else -> buildModel.declaredProperties.find { it.name == name }!!
      }
    }

    run {
      val stringModel = findProperty("prop1")
      assertEquals("value", stringModel.valueAsString())
      assertNull(stringModel.toInt())
      assertNull(stringModel.toBoolean())
      assertNull(stringModel.toList())
      assertNull(stringModel.toMap())
      val intModel = findProperty("prop2")
      assertEquals(25, intModel.toInt())
      assertEquals("25", intModel.valueAsString())
      assertNull(intModel.toBoolean())
      assertNull(intModel.toMap())
      assertNull(intModel.toList())
      val boolModel = findProperty("prop3")
      assertEquals(true, boolModel.toBoolean())
      assertEquals("true", boolModel.valueAsString())
      assertNull(boolModel.toInt())
      val mapModel = findProperty("prop4")
      assertNotNull(mapModel.toMap())
      assertNull(mapModel.toInt())
      assertNull(mapModel.toList())
      val listModel = findProperty("prop5")
      assertNotNull(listModel.toList())
      assertNull(listModel.toBoolean())
      assertNull(listModel.toMap())
    }
  }

  @Test
  fun testGetNonQuotedListIndex() {
    writeToBuildFile(TestFile.GET_NON_QUOTED_LIST_INDEX)

    val extModel = gradleBuildModel.ext()

    val firstModel = extModel.findProperty("prop2")
    verifyPropertyModel(firstModel.resolve(), INTEGER_TYPE, 1, INTEGER, REGULAR, 1)
    verifyPropertyModel(firstModel, STRING_TYPE, "prop1[0]", REFERENCE, REGULAR, 1)
    val secondModel = extModel.findProperty("prop3")
    verifyPropertyModel(secondModel.resolve(), STRING_TYPE, "two", STRING, REGULAR, 1)
    verifyPropertyModel(secondModel, STRING_TYPE, "prop1[1]", REFERENCE, REGULAR, 1)
  }

  @Test
  fun testReferencePropertyDependency() {
    writeToBuildFile(TestFile.REFERENCE_PROPERTY_DEPENDENCY)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals(extraName("prop1"), propertyModel.getValue(STRING_TYPE))
    assertEquals(extraName("prop1"), propertyModel.getRawValue(STRING_TYPE))
    assertEquals(REFERENCE, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    val deps = propertyModel.dependencies
    assertSize(1, deps)
    val value = deps[0]
    assertEquals("value", value.getValue(STRING_TYPE))
    assertEquals("value", value.getRawValue(STRING_TYPE))
    assertEquals(STRING, value.valueType)
    assertEquals(REGULAR, value.propertyType)
  }

  @Test
  fun testIntegerReferencePropertyDependency() {
    writeToBuildFile(TestFile.INTEGER_REFERENCE_PROPERTY_DEPENDENCY)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("prop1", propertyModel.getValue(STRING_TYPE))
    assertEquals("prop1", propertyModel.getRawValue(STRING_TYPE))
    assertEquals(REFERENCE, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    val deps = propertyModel.dependencies
    assertSize(1, deps)
    val value = deps[0]
    assertEquals(25, value.getValue(INTEGER_TYPE))
    assertEquals(25, value.getRawValue(INTEGER_TYPE))
    assertEquals(INTEGER, value.valueType)
    assertEquals(REGULAR, value.propertyType)
  }

  @Test
  fun testReferenceVariableDependency() {
    writeToBuildFile(TestFile.REFERENCE_VARIABLE_DEPENDENCY)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("prop1", propertyModel.getValue(STRING_TYPE))
    assertEquals("prop1", propertyModel.getRawValue(STRING_TYPE))
    assertEquals(REFERENCE, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    val deps = propertyModel.dependencies
    assertSize(1, deps)
    val value = deps[0]
    assertEquals("value", value.getValue(STRING_TYPE))
    assertEquals("value", value.getRawValue(STRING_TYPE))
    assertEquals(STRING, value.valueType)
    assertEquals(VARIABLE, value.propertyType)
  }

  @Test
  fun testCreateAndDeleteListToEmpty() {
    writeToBuildFile(TestFile.CREATE_AND_DELETE_LIST_TO_EMPTY)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    run {
      val propertyModel = extModel.findProperty("prop2")
      assertMissingProperty(propertyModel)
      propertyModel.addListValue().setValue("true")
      verifyListProperty(propertyModel, listOf("true"), true)
      val valueModel = propertyModel.getListValue("true")!!
      verifyPropertyModel(valueModel, STRING_TYPE, "true", STRING, DERIVED, 0, "0")
      valueModel.delete()
      assertMissingProperty(valueModel)
      verifyListProperty(propertyModel, listOf(), true)
    }

    applyChangesAndReparse(buildModel)
    // TODO(b/148198247): we need type decorators on the empty list in KotlinScript
    verifyFileContents(myBuildFile, TestFile.CREATE_AND_DELETE_LIST_TO_EMPTY_EXPECTED)

    run {
      val propertyModel = extModel.findProperty("prop2")
      verifyListProperty(propertyModel, listOf(), true)
    }
  }

  @Test
  fun testCreateAndDeletePlaceHoldersToEmpty() {
    writeToBuildFile(TestFile.CREATE_AND_DELETE_PLACE_HOLDERS_TO_EMPTY)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.android().defaultConfig().manifestPlaceholders()
      verifyEmptyMapProperty(propertyModel)
      propertyModel.getMapValue("key").setValue("true")
      verifyMapProperty(propertyModel, mapOf("key" to "true"))
      val valueModel = propertyModel.getMapValue("key")
      verifyPropertyModel(valueModel, STRING_TYPE, "true", STRING, DERIVED, 0, "key")
      valueModel.delete()
      assertMissingProperty(valueModel)
      verifyEmptyMapProperty(propertyModel)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.CREATE_AND_DELETE_PLACE_HOLDERS_TO_EMPTY_EXPECTED)

    run {
      val propertyModel = buildModel.android().defaultConfig().manifestPlaceholders()
      verifyEmptyMapProperty(propertyModel)
    }
  }

  @Test
  fun testCreateAndDeleteMapToEmpty() {
    writeToBuildFile(TestFile.CREATE_AND_DELETE_MAP_TO_EMPTY)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    run {
      val propertyModel = extModel.findProperty("prop2")
      assertMissingProperty(propertyModel)
      propertyModel.getMapValue("key").setValue("true")
      verifyMapProperty(propertyModel, mapOf("key" to "true"))
      val valueModel = propertyModel.getMapValue("key")
      verifyPropertyModel(valueModel, STRING_TYPE, "true", STRING, DERIVED, 0, "key")
      valueModel.delete()
      assertMissingProperty(valueModel)
      verifyMapProperty(propertyModel, mapOf())
    }

    applyChangesAndReparse(buildModel)
    // TODO(b/148198247): we need type decorators on the empty map in KotlinScript
    verifyFileContents(myBuildFile, TestFile.CREATE_AND_DELETE_MAP_TO_EMPTY_EXPECTED)

    run {
      val propertyModel = extModel.findProperty("prop2")
      verifyMapProperty(propertyModel, mapOf())
    }
  }

  @Test
  fun testReferenceMapDependency() {
    writeToBuildFile(TestFile.REFERENCE_MAP_DEPENDENCY)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")

    assertEquals(REFERENCE, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)
    assertEquals(extraName("prop1"), propertyModel.getRawValue(STRING_TYPE))
    assertEquals(extraName("prop1"), propertyModel.getValue(STRING_TYPE))

    assertSize(1, propertyModel.dependencies)
    val dep = propertyModel.dependencies[0]
    assertEquals(MAP, dep.valueType)
    assertEquals(REGULAR, dep.propertyType)

    val map = dep.getValue(MAP_TYPE)!!
    assertSize(1, map.entries)
    val mapValue = map["key"]!!
    assertEquals(STRING, mapValue.valueType)
    assertEquals(DERIVED, mapValue.propertyType)
    assertEquals("value", mapValue.getValue(STRING_TYPE))
  }

  @Test
  fun testReferenceListDependency() {
    writeToBuildFile(TestFile.REFERENCE_LIST_DEPENDENCY)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals(REFERENCE, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)
    assertEquals(extraName("prop1"), propertyModel.getRawValue(STRING_TYPE))
    assertEquals(extraName("prop1"), propertyModel.getValue(STRING_TYPE))

    assertSize(1, propertyModel.dependencies)
    val dep = propertyModel.dependencies[0]
    assertEquals(LIST, dep.valueType)
    assertEquals(REGULAR, dep.propertyType)

    val list = dep.getValue(LIST_TYPE)!!
    assertSize(2, list)

    // Check the first list value
    val firstItem = list[0]
    assertEquals(INTEGER, firstItem.valueType)
    assertEquals(DERIVED, firstItem.propertyType)
    assertEquals(1, firstItem.getValue(INTEGER_TYPE))

    val secondItem = list[1]
    assertEquals(BOOLEAN, secondItem.valueType)
    assertEquals(DERIVED, secondItem.propertyType)
    assertEquals(true, secondItem.getValue(BOOLEAN_TYPE))
  }

  @Test
  fun testPropertyDependency() {
    writeToBuildFile(TestFile.PROPERTY_DEPENDENCY)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("\${${extraName("prop1")}} world!", propertyModel.getRawValue(STRING_TYPE))
    assertEquals("hello world!", propertyModel.getValue(STRING_TYPE))
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    val dependencies = propertyModel.dependencies

    assertSize(1, dependencies)
    val dep = dependencies[0]
    assertEquals(STRING, dep.valueType)
    assertEquals(REGULAR, dep.propertyType)
    assertEquals("hello", dep.getValue(STRING_TYPE))
  }

  @Test
  fun testVariableDependency() {
    writeToBuildFile(TestFile.VARIABLE_DEPENDENCY)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()
    val propertyModel = when {
      isGroovy -> extModel.findProperty("prop2")
      else -> buildModel.declaredProperties.find { it.name == "prop2" }!!
    }
    assertEquals("\${prop1} world!", propertyModel.getRawValue(STRING_TYPE))
    assertEquals("hello world!", propertyModel.getValue(STRING_TYPE))
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(VARIABLE, propertyModel.propertyType)

    val dependencies = propertyModel.dependencies

    assertSize(1, dependencies)
    val dep = dependencies[0]
    assertEquals(STRING, dep.valueType)
    assertEquals(VARIABLE, dep.propertyType)
    assertEquals("hello", dep.getValue(STRING_TYPE))
  }

  @Test
  fun testPropertyVariableDependency() {
    writeToBuildFile(TestFile.PROPERTY_VARIABLE_DEPENDENCY)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("\${prop1} world!", propertyModel.getRawValue(STRING_TYPE))
    assertEquals("hello world!", propertyModel.getValue(STRING_TYPE))
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    val dependencies = propertyModel.dependencies

    assertSize(1, dependencies)
    val dep = dependencies[0]
    assertEquals(STRING, dep.valueType)
    assertEquals(VARIABLE, dep.propertyType)
    assertEquals("hello", dep.getValue(STRING_TYPE))
  }

  @Test
  fun testVariablePropertyDependency() {
    writeToBuildFile(TestFile.VARIABLE_PROPERTY_DEPENDENCY)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()
    val propertyModel = when {
      isGroovy -> extModel.findProperty("prop2")
      else -> buildModel.declaredProperties.find { it.name == "prop2" }!!
    }
    assertEquals(if (isGroovy) "\${prop1} world!" else "\${extra[\"prop1\"]} world!", propertyModel.getRawValue(STRING_TYPE))
    assertEquals("hello world!", propertyModel.getValue(STRING_TYPE))
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(VARIABLE, propertyModel.propertyType)

    val dependencies = propertyModel.dependencies

    assertSize(1, dependencies)
    val dep = dependencies[0]
    assertEquals(STRING, dep.valueType)
    assertEquals(REGULAR, dep.propertyType)
    assertEquals("hello", dep.getValue(STRING_TYPE))
  }

  @Test
  fun testMultipleDependenciesWithFullyQualifiedName() {
    assumeTrue("extra[\"foo\"] erroneously shadows val foo in KotlinScript parser", !isKotlinScript) // TODO(b/148939007)
    writeToBuildFile(TestFile.MULTIPLE_DEPENDENCIES_WITH_FULLY_QUALIFIED_NAME)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("value2 and value1", propertyModel.getValue(STRING_TYPE))
    assertEquals("\${prop1} and \${project.ext.prop1}", propertyModel.getRawValue(STRING_TYPE))

    // Check the dependencies are correct
    val deps = propertyModel.dependencies
    assertSize(2, deps)

    run {
      val value = deps[0]
      assertEquals("prop1", value.name)
      assertEquals("value2", value.getValue(STRING_TYPE))
      assertEquals("ext.prop1", value.fullyQualifiedName)
      assertEquals(STRING, value.valueType)
      assertEquals(VARIABLE, value.propertyType)
    }

    run {
      val value = deps[1]
      assertEquals("prop1", value.name)
      assertEquals("value1", value.getValue(STRING_TYPE))
      assertEquals("ext.prop1", value.fullyQualifiedName)
      assertEquals(STRING, value.valueType)
      assertEquals(REGULAR, value.propertyType)
    }
  }

  @Test
  fun testMultipleTypeDependenciesWithFullyQualifiedName() {
    assumeTrue("extra[\"foo\"] erroneously shadows val foo in KotlinScript parser", !isKotlinScript) // TODO(b/148939007)
    writeToBuildFile(TestFile.MULTIPLE_TYPE_DEPENDENCIES_WITH_FULLY_QUALIFIED_NAME)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("true and value1", propertyModel.getValue(STRING_TYPE))
    assertEquals(if (isGroovy) "\${prop1} and \${project.ext.prop1}" else "\${prop1} and \${project.extra[\"prop1\"]}", propertyModel.getRawValue(STRING_TYPE))

    // Check the dependencies are correct
    val deps = propertyModel.dependencies
    assertSize(2, deps)

    run {
      val value = deps[0]
      assertEquals("prop1", value.name)
      assertEquals(true, value.getValue(BOOLEAN_TYPE))
      assertEquals("ext.prop1", value.fullyQualifiedName)
      assertEquals(BOOLEAN, value.valueType)
      assertEquals(VARIABLE, value.propertyType)
    }

    run {
      val value = deps[1]
      assertEquals("prop1", value.name)
      assertEquals("value1", value.getValue(STRING_TYPE))
      assertEquals(if (isGroovy) "ext.prop1" else "prop1", value.fullyQualifiedName)
      assertEquals(STRING, value.valueType)
      assertEquals(REGULAR, value.propertyType)
    }
  }

  @Test
  fun testNestedListPropertyInjection() {
    writeToBuildFile(TestFile.NESTED_LIST_PROPERTY_INJECTION)

    val propertyModel = gradleBuildModel.ext().findProperty("prop4")
    assertEquals("3", propertyModel.getValue(STRING_TYPE))
    assertEquals(if (isGroovy) "${'$'}{prop3.key[0][2]}" else "\${prop3[\"key\"][0][2]}", propertyModel.getRawValue(STRING_TYPE))
    assertEquals(REGULAR, propertyModel.propertyType)
    assertEquals(STRING, propertyModel.valueType)

    val dependencies = propertyModel.dependencies
    assertSize(1, dependencies)
    val depModel = dependencies[0]
    assertEquals(INTEGER, depModel.valueType)
    assertEquals(DERIVED, depModel.propertyType)
    assertEquals(3, depModel.getValue(INTEGER_TYPE))
    assertSize(0, depModel.dependencies)
  }

  @Test
  fun testNestedMapVariableInjection() {
    writeToBuildFile(TestFile.NESTED_MAP_VARIABLE_INJECTION)

    val propertyModel = when {
      isGroovy -> gradleBuildModel.ext().findProperty("prop3")
      else -> gradleBuildModel.declaredProperties.find { it.name == "prop3" }!!
    }
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(VARIABLE, propertyModel.propertyType)
    assertEquals("valuetrue", propertyModel.getValue(STRING_TYPE))
    assertEquals("${'$'}{prop2[\"key2\"][\"key1\"]}", propertyModel.getRawValue(STRING_TYPE))

    val dependencies = propertyModel.dependencies
    assertSize(1, dependencies)
    val depModel = dependencies[0]
    assertEquals(STRING, depModel.valueType)
    assertEquals(DERIVED, depModel.propertyType)
    assertEquals("valuetrue", depModel.getValue(STRING_TYPE))
    assertEquals("value${'$'}{prop}", depModel.getRawValue(STRING_TYPE))

    val dependencies2 = depModel.dependencies
    assertSize(1, dependencies2)
    val depModel2 = dependencies2[0]
    assertEquals(BOOLEAN, depModel2.valueType)
    assertEquals(REGULAR, depModel2.propertyType)
    assertEquals(true, depModel2.getValue(BOOLEAN_TYPE))
    assertEquals(true, depModel2.getRawValue(BOOLEAN_TYPE))
    assertSize(0, depModel2.dependencies)
  }

  @Test
  fun testListDependency() {
    writeToBuildFile(TestFile.LIST_DEPENDENCY)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("value", propertyModel.getValue(STRING_TYPE))
    assertEquals("\${prop1[0]}", propertyModel.getRawValue(STRING_TYPE))

    val deps = propertyModel.dependencies
    assertSize(1, deps)
    val value = deps[0]
    assertEquals("value", value.getValue(STRING_TYPE))
    assertEquals(DERIVED, value.propertyType)
    assertEquals(STRING, value.valueType)
  }

  @Test
  fun testMapDependency() {
    writeToBuildFile(TestFile.MAP_DEPENDENCY)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("value", propertyModel.getValue(STRING_TYPE))
    assertEquals(if (isGroovy) "\${prop1.key}" else "\${prop1[\"key\"]}", propertyModel.getRawValue(STRING_TYPE))

    val deps = propertyModel.dependencies
    val value = deps[0]
    assertEquals("value", value.getValue(STRING_TYPE))
    assertEquals(DERIVED, value.propertyType)
    assertEquals(STRING, value.valueType)
  }

  @Test
  fun testOutOfScopeMapAndListDependencies() {
    writeToBuildFile(TestFile.OUT_OF_SCOPE_MAP_AND_LIST_DEPENDENCIES)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop4")
    assertEquals("value1 and value2 and value3", propertyModel.getValue(STRING_TYPE))

    val deps = propertyModel.dependencies
    assertSize(3, deps)

    run {
      val value = deps[0]
      assertEquals("value1", value.getValue(STRING_TYPE))
      assertEquals(STRING, value.valueType)
      assertEquals(VARIABLE, value.propertyType)
    }

    run {
      val value = deps[1]
      assertEquals("value2", value.getValue(STRING_TYPE))
      assertEquals(STRING, value.valueType)
      assertEquals(DERIVED, value.propertyType)
    }

    run {
      val value = deps[2]
      assertEquals("value3", value.getValue(STRING_TYPE))
      assertEquals(STRING, value.valueType)
      assertEquals(DERIVED, value.propertyType)
    }
  }

  @Test
  fun testDeepDependencies() {
    writeToBuildFile(TestFile.DEEP_DEPENDENCIES)

    val extModel = gradleBuildModel.ext()
    var expected = "987654321"
    val propertyModel = extModel.findProperty("prop9")
    assertEquals(expected, propertyModel.getValue(STRING_TYPE))
    assertEquals("9\${${extraName("prop8")}}", propertyModel.getRawValue(STRING_TYPE))
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)

    var deps = propertyModel.dependencies
    for (i in 1..7) {
      assertSize(1, deps)
      val value = deps[0]
      expected = expected.drop(1)
      assertEquals(expected, value.getValue(STRING_TYPE))
      assertEquals("${9 - i}\${${extraName("prop${8 - i}")}}", value.getRawValue(STRING_TYPE))
      assertEquals(STRING, propertyModel.valueType)
      assertEquals(REGULAR, propertyModel.propertyType)
      deps = deps[0].dependencies
    }

    assertSize(1, deps)
    val value = deps[0]
    assertEquals("1", value.getValue(STRING_TYPE))
    assertEquals("1", value.getRawValue(STRING_TYPE))
    assertEquals(STRING, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)
  }

  @Test
  fun testDependenciesInMap() {
    writeToBuildFile(TestFile.DEPENDENCIES_IN_MAP)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop4")
    assertEquals(MAP, propertyModel.valueType)
    assertEquals(REGULAR, propertyModel.propertyType)
    assertEquals("prop4", propertyModel.name)
    assertEquals("ext.prop4", propertyModel.fullyQualifiedName)
    val deps = propertyModel.dependencies
    assertSize(2, deps)

    val map = propertyModel.getValue(MAP_TYPE)!!
    assertSize(2, map.entries)

    run {
      val value = map["key1"]!!
      assertEquals(REFERENCE, value.valueType)
      assertEquals(DERIVED, value.propertyType)
      assertEquals(extraName("prop1"), value.getValue(STRING_TYPE))
      assertEquals("key1", value.name)
      assertEquals("ext.prop4.key1", value.fullyQualifiedName)

      val valueDeps = value.dependencies
      assertSize(1, valueDeps)
      val depValue = valueDeps[0]
      checkContainsValue(deps as Collection<GradlePropertyModel>, depValue)
      assertEquals(INTEGER, depValue.valueType)
      assertEquals(REGULAR, depValue.propertyType)
      assertEquals(25, depValue.getValue(INTEGER_TYPE))
      assertEquals("prop1", depValue.name)
      assertEquals("ext.prop1", depValue.fullyQualifiedName)
    }

    run {
      val value = map["key2"]!!
      assertEquals(STRING, value.valueType)
      assertEquals(DERIVED, value.propertyType)
      assertEquals("false", value.getValue(STRING_TYPE))
      assertEquals("key2", value.name)
      assertEquals("ext.prop4.key2", value.fullyQualifiedName)

      val valueDeps = value.dependencies
      assertSize(1, valueDeps)
      val depValue = valueDeps[0]
      checkContainsValue(deps as Collection<GradlePropertyModel>, depValue)
      assertEquals(BOOLEAN, depValue.valueType)
      assertEquals(REGULAR, depValue.propertyType)
      assertEquals(false, depValue.getValue(BOOLEAN_TYPE))
      assertEquals("prop2", depValue.name)
      assertEquals("ext.prop2", depValue.fullyQualifiedName)
    }
  }

  @Test
  fun testGetFile() {
    writeToBuildFile(TestFile.GET_FILE)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop1")
    assertEquals(propertyModel.gradleFile, myBuildFile)
  }

  @Test
  fun testPropertySetValue() {
    runSetPropertyTest(TestFile.PROPERTY_SET_VALUE, REGULAR)
  }

  @Test
  fun testVariableSetValue() {
    runSetPropertyTest(TestFile.VARIABLE_SET_VALUE, VARIABLE)
  }

  @Test
  fun testSetUnknownValueType() {
    writeToBuildFile(TestFile.SET_UNKNOWN_VALUE_TYPE)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello", STRING, REGULAR, 0, "prop1", "ext.prop1")
      propertyModel.setValue(25)
      verifyPropertyModel(propertyModel, INTEGER_TYPE, 25, INTEGER, REGULAR, 0, "prop1", "ext.prop1")
      propertyModel.setValue(true)
      verifyPropertyModel(propertyModel, BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "prop1", "ext.prop1")
      propertyModel.setValue("goodbye")
      verifyPropertyModel(propertyModel, STRING_TYPE, "goodbye", STRING, REGULAR, 0, "prop1", "ext.prop1")

      try {
        propertyModel.setValue(File("Hello"))
        fail()
      }
      catch (e: IllegalArgumentException) {
        // Expected
      }
      try {
        propertyModel.setValue(IllegalStateException("Boo"))
        fail()
      }
      catch (e: IllegalArgumentException) {
        // Expected
      }

      verifyPropertyModel(propertyModel, STRING_TYPE, "goodbye", STRING, REGULAR, 0, "prop1", "ext.prop1")
    }
  }

  @Test
  fun testEscapeSetStrings() {
    assumeTrue("parsing and writing escapes in strings not currently working in KotlinScript", !isKotlinScript) // TODO(b/148939103)
    writeToBuildFile(TestFile.ESCAPE_SET_STRINGS)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertMissingProperty(propertyModel)
      propertyModel.setValue(iStr("\nNewLines\n\tWith\n\tSome\n\tTabs\n"))
      verifyPropertyModel(propertyModel, STRING_TYPE, "\nNewLines\n\tWith\n\tSome\n\tTabs\n", STRING, REGULAR, 0)
      assertEquals("\nNewLines\n\tWith\n\tSome\n\tTabs\n", propertyModel.getRawValue(STRING_TYPE))
      val literalModel = buildModel.ext().findProperty("prop2")
      assertMissingProperty(literalModel)
      literalModel.setValue("\nNewLines\n\tWith\n\tSome\n\tTabs\n")
      verifyPropertyModel(literalModel, STRING_TYPE, "\nNewLines\n\tWith\n\tSome\n\tTabs\n", STRING, REGULAR, 0)
      assertEquals("\nNewLines\n\tWith\n\tSome\n\tTabs\n", literalModel.getRawValue(STRING_TYPE))
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "\nNewLines\n\tWith\n\tSome\n\tTabs\n", STRING, REGULAR, 0)
      assertEquals("\nNewLines\n\tWith\n\tSome\n\tTabs\n", propertyModel.getRawValue(STRING_TYPE))
      val literalModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(literalModel, STRING_TYPE, "\nNewLines\n\tWith\n\tSome\n\tTabs\n", STRING, REGULAR, 0)
      assertEquals("\nNewLines\n\tWith\n\tSome\n\tTabs\n", literalModel.getRawValue(STRING_TYPE))
    }
  }

  @Test
  fun testQuotesInString() {
    assumeTrue("parsing and writing escaped quotes not currently working in KotlinScript", !isKotlinScript) // TODO(b/148939103)
    writeToBuildFile(TestFile.QUOTES_IN_STRING)
    val buildModel = gradleBuildModel

    run {
      val literalModel = buildModel.ext().findProperty("prop1")
      assertMissingProperty(literalModel)
      literalModel.setValue("'these should be escaped' \"But these shouldn't\"")
      verifyPropertyModel(literalModel, STRING_TYPE, "'these should be escaped' \"But these shouldn't\"", STRING, REGULAR, 0)
      assertEquals("'these should be escaped' \"But these shouldn't\"", literalModel.getRawValue(STRING_TYPE))

      val gStringModel = buildModel.ext().findProperty("prop2")
      assertMissingProperty(gStringModel)
      gStringModel.setValue(iStr("'these should not be escaped' \"But these should be\""))
      verifyPropertyModel(gStringModel, STRING_TYPE, "'these should not be escaped' \"But these should be\"", STRING, REGULAR, 0)
      assertEquals("'these should not be escaped' \"But these should be\"", gStringModel.getRawValue(STRING_TYPE))
    }

    applyChangesAndReparse(buildModel)

    run {
      val literalModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(literalModel, STRING_TYPE, "'these should be escaped' \"But these shouldn't\"", STRING, REGULAR, 0)
      assertEquals("'these should be escaped' \"But these shouldn't\"", literalModel.getRawValue(STRING_TYPE))
      val gStringModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(gStringModel, STRING_TYPE, "'these should not be escaped' \"But these should be\"", STRING, REGULAR, 0)
      assertEquals("'these should not be escaped' \"But these should be\"", gStringModel.getRawValue(STRING_TYPE))
    }
  }

  @Test
  fun testSetBothStringTypes() {
    isIrrelevantForKotlinScript("only one String type in KotlinScript")
    writeToBuildFile(TestFile.SET_BOTH_STRING_TYPES)
    val buildModel = gradleBuildModel

    run {
      // Set the literal string
      val literalProperty = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(literalProperty, STRING_TYPE, "Value1", STRING, VARIABLE, 0)
      literalProperty.setValue("I watched as the ${'$'}{lamb}")
      verifyPropertyModel(literalProperty, STRING_TYPE, "I watched as the ${'$'}{lamb}", STRING, VARIABLE, 0)

      // Set the interpolated string
      val interpolatedProperty = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(interpolatedProperty, STRING_TYPE, "Value2", STRING, REGULAR, 0)
      interpolatedProperty.setValue(iStr("opened the first of the ${'$'}{seven} seals"))
      verifyPropertyModel(interpolatedProperty, STRING_TYPE, "opened the first of the sêvĕn seals", STRING, REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)

    // Check the properties after a reparse.
    run {
      val literalProperty = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(literalProperty, STRING_TYPE, "I watched as the ${'$'}{lamb}", STRING, VARIABLE, 0)

      val interpolatedProperty = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(interpolatedProperty, STRING_TYPE, "opened the first of the sêvĕn seals", STRING, REGULAR, 1)

      // Check the dependency is correct.
      val dependencyModel = interpolatedProperty.dependencies[0]
      verifyPropertyModel(dependencyModel, STRING_TYPE, "sêvĕn", STRING, REGULAR, 0)
    }
  }

  @Test
  fun testSetGarbageReference() {
    assumeTrue("relies on Groovy-specific Psi insertion behaviour with malformed input", isGroovy)
    writeToBuildFile(TestFile.SET_GARBAGE_REFERENCE)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)

      propertyModel.setValue(ReferenceTo("in a voice like thunder"))
      // Note: Since this doesn't actually make any sense, the word "in" gets removed as it is a keyword in Groovy.
      verifyPropertyModel(propertyModel, STRING_TYPE, "a voice like thunder", UNKNOWN, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "a voice like thunder", UNKNOWN, REGULAR, 0)
    }
  }

  @Test
  fun testSetReferenceWithModel() {
    writeToBuildFile(TestFile.SET_REFERENCE_WITH_MODEL)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "and there before me was a white horse!", STRING, REGULAR, 0)

      val otherModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(otherModel, STRING_TYPE, "“Come and see!” I looked", STRING, REGULAR, 0)

      // Set prop2 to refer to prop1
      propertyModel.setValue(ReferenceTo(otherModel))
      // TODO(b/148938992): in KotlinScript when we set the value, the explicit cast is included in the reference, whereas when we parse
      //  (see below) the cast is excluded.  Possibly this is all at the wrong level of abstraction anyway :-(
      verifyPropertyModel(propertyModel, STRING_TYPE, if (isGroovy) "prop1" else "extra[\"prop1\"] as String", REFERENCE, REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.SET_REFERENCE_WITH_MODEL_EXPECTED)

    // Check the value
    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, if (isGroovy) "prop1" else "extra[\"prop1\"]", REFERENCE, REGULAR, 1)
    }
  }

  @Test
  fun testQuotesWithinQuotes() {
    assumeTrue("parsing and writing escaped quotes not currently working in KotlinScript", !isKotlinScript) // TODO(b/148939103)
    writeToBuildFile(TestFile.QUOTES_WITHIN_QUOTES)
    val buildModel = gradleBuildModel

    // Check we read the string correctly
    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "\"\"\"Hello \"\"\"", STRING, REGULAR, 0)
    }

    // Check we can set strings with quotes
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      propertyModel.setValue(iStr("\"Come and see!\" I looked"))
      verifyPropertyModel(propertyModel, STRING_TYPE, "\"Come and see!\" I looked", STRING, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.QUOTES_WITHIN_QUOTES_EXPECTED)

    // Check it's correct after a reparse.
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "\"Come and see!\" I looked", STRING, REGULAR, 0)
    }
  }

  @Test
  fun testSetReferenceValue() {
    writeToBuildFile(TestFile.SET_REFERENCE_VALUE)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop2", REFERENCE, REGULAR, 1)

      propertyModel.setValue(ReferenceTo("prop1"))
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.SET_REFERENCE_VALUE_EXPECTED)

    // Check the the reference has changed.
    run {
      val propertyModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)
    }
  }

  @Test
  fun testChangePropertyTypeToReference() {
    writeToBuildFile(TestFile.CHANGE_PROPERTY_TYPE_TO_REFERENCE)
    val buildModel = gradleBuildModel

    run {
      // Check the unused property as well
      val unusedModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(unusedModel, STRING_TYPE, "25", STRING, REGULAR, 0)

      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0)

      // Set to a reference.

      propertyModel.setValue(ReferenceTo("prop1"))
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.CHANGE_PROPERTY_TYPE_TO_REFERENCE_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)

      val unusedModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(unusedModel, STRING_TYPE, "25", STRING, REGULAR, 0)
    }
  }

  @Test
  fun testChangePropertyTypeToLiteral() {
    writeToBuildFile(TestFile.CHANGE_PROPERTY_TYPE_TO_LITERAL)
    val buildModel = gradleBuildModel

    run {
      // Check referred to value.
      val intModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(intModel, INTEGER_TYPE, 25, INTEGER, REGULAR, 0)

      // Check the reference
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)

      // Set the value, and check again
      propertyModel.setValue(iStr("${'$'}{prop1}"))
      verifyPropertyModel(propertyModel, STRING_TYPE, "25", STRING, REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.CHANGE_PROPERTY_TYPE_TO_LITERAL_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "25", STRING, REGULAR, 1)

      // Ensure the referred value is still correct.
      val intModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(intModel, INTEGER_TYPE, 25, INTEGER, REGULAR, 0)
    }
  }

  @Test
  fun testDependencyChangedUpdatesValue() {
    writeToBuildFile(TestFile.DEPENDENCY_CHANGED_UPDATES_VALUE)
    val buildModel = gradleBuildModel

    run {
      // Check the properties are correct.
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello", STRING, REGULAR, 0)
      val propertyModel2 = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel2, STRING_TYPE, "hello world!", STRING, REGULAR, 1)
      assertEquals("${'$'}{prop1} world!", propertyModel2.getRawValue(STRING_TYPE))
    }

    run {
      // Ensure changing prop1 changes the value of prop2.
      val propertyModel = buildModel.ext().findProperty("prop1")
      val newValue = "goodbye"
      propertyModel.setValue(newValue)
      verifyPropertyModel(propertyModel, STRING_TYPE, newValue, STRING, REGULAR, 0)
      val propertyModel2 = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel2, STRING_TYPE, "goodbye world!", STRING, REGULAR, 1)
      // Check dependency is correct.
      verifyPropertyModel(propertyModel2.dependencies[0], STRING_TYPE, newValue, STRING, REGULAR, 0)

      // Apply, reparse and check again.
      applyChangesAndReparse(buildModel)
      verifyFileContents(myBuildFile, TestFile.DEPENDENCY_CHANGED_UPDATES_VALUE_EXPECTED)

      val propertyModel3 = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel3, STRING_TYPE, newValue, STRING, REGULAR, 0)
      val propertyModel4 = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel4, STRING_TYPE, "goodbye world!", STRING, REGULAR, 1)
      assertEquals("${'$'}{prop1} world!", propertyModel2.getRawValue(STRING_TYPE))
      // Check dependency is correct.
      verifyPropertyModel(propertyModel4.dependencies[0], STRING_TYPE, newValue, STRING, REGULAR, 0)
    }
  }

  @Test
  fun testDependencyBasicCycle() {
    writeToBuildFile(TestFile.DEPENDENCY_BASIC_CYCLE)
    val buildModel = gradleBuildModel
    val propertyModel = buildModel.ext().findProperty("prop1")
    verifyPropertyModel(propertyModel, STRING_TYPE, "${'$'}{${extraName("prop1")}}", STRING, REGULAR, 0)
  }

  @Test
  fun testDependencyBasicCycleReference() {
    writeToBuildFile(TestFile.DEPENDENCY_BASIC_CYCLE_REFERENCE)
    val buildModel = gradleBuildModel
    val propertyModel = buildModel.ext().findProperty("prop1")
    verifyPropertyModel(propertyModel, STRING_TYPE, extraName("prop1"), REFERENCE, REGULAR, 0)
  }

  @Test
  fun testDependencyNoCycle4Depth() {
    writeToBuildFile(TestFile.DEPENDENCY_NO_CYCLE4_DEPTH)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop4")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Value", STRING, REGULAR, 1)
    }
  }

  @Test
  fun testDependencyTwice() {
    writeToBuildFile(TestFile.DEPENDENCY_TWICE)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Value + Value", STRING, REGULAR, 2)
    }
  }

  @Test
  fun testDependencyNoCycle() {
    writeToBuildFile(TestFile.DEPENDENCY_NO_CYCLE)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "abcValue", STRING, REGULAR, 1)
    }

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "bcValue", STRING, REGULAR, 1)
    }
  }

  @Test
  fun testDeleteProperty() {
    writeToBuildFile(TestFile.DELETE_PROPERTY)
    val buildModel = gradleBuildModel

    // Delete the property
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      propertyModel.delete()
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, "")

    // Check everything has been deleted
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(NONE, propertyModel.valueType)
    }
  }

  @Test
  fun testDeleteVariable() {
    writeToBuildFile(TestFile.DELETE_VARIABLE)
    val buildModel = gradleBuildModel

    // Delete the property
    run {
      val propertyModel = when {
        isGroovy -> buildModel.ext().findProperty("prop1")
        else -> buildModel.declaredProperties.find { it.name == "prop1" }!!
      }
      propertyModel.delete()
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.DELETE_VARIABLE_EXPECTED)

    // Check everything has been deleted
    run {
      val propertyModel = when {
        isGroovy -> buildModel.ext().findProperty("prop1")
        // TODO(b/148938436): how can we get a model for a variable not-yet-defined at toplevel?  For now we can return without losing
        //  too much, but this is a more general question
        else -> buildModel.declaredProperties.find { it.name == "prop1" } ?: return
      }
      assertEquals(NONE, propertyModel.valueType)
    }
  }

  @Test
  fun testDeleteAndResetProperty() {
    writeToBuildFile(TestFile.DELETE_AND_RESET_PROPERTY)
    val buildModel = gradleBuildModel

    verifyDeleteAndResetProperty(buildModel)
  }

  @Test
  fun testDeleteAndResetKTSArrayExpressionProperty() {
    arrayExpressionSyntaxTestIsIrrelevantForGroovy()
    writeToBuildFile(TestFile.DELETE_AND_RESET_KTS_ARRAY_EXPRESSION_PROPERTY)
    val buildModel = gradleBuildModel

    verifyDeleteAndResetProperty(buildModel)
  }

  @Test
  fun testUpdatePropertyValueWithoutSyntaxChange() {
    writeToBuildFile(TestFile.UPDATE_PROPERTY_WITHOUT_SYNTAX_CHANGE)
    val buildModel = gradleBuildModel

    run {
      val property1Model = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(property1Model, STRING_TYPE, "val1", STRING, REGULAR, 0)
      property1Model.setValue(123)
    }

    run {
      val property2Model = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(property2Model, STRING_TYPE, "val2", STRING, REGULAR, 0)
      property2Model.setValue(true)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.UPDATE_PROPERTY_WITHOUT_SYNTAX_CHANGE_EXPECTED)
  }

  @Test
  fun testDeleteEmptyProperty() {
    val text = ""
    writeToBuildFile(text)
    val buildModel = gradleBuildModel

    // Delete a nonexistent property
    run {
      val propertyModel = buildModel.ext().findProperty("coolpropertyname")
      propertyModel.delete()
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, "")

    run {
      val propertyModel = buildModel.ext().findProperty("coolpropertyname")
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)
    }
  }

  @Test
  fun testDeleteVariableDependency() {
    writeToBuildFile(TestFile.DELETE_VARIABLE_DEPENDENCY)
    val buildModel = gradleBuildModel

    // Get the model to delete
    run {
      val firstPropertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(firstPropertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)

      val secondPropertyModel = firstPropertyModel.dependencies[0]
      verifyPropertyModel(secondPropertyModel, STRING_TYPE, "value", STRING, VARIABLE, 0)
      // Delete the model
      secondPropertyModel.delete()

      // After deleting this property, the value of the first property shouldn't change since it is just a reference to nothing.
      // However it will no longer have a dependency.
      verifyPropertyModel(firstPropertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.DELETE_VARIABLE_DEPENDENCY_EXPECTED)

    run {
      val firstPropertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(firstPropertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 0)
    }
  }

  @Test
  fun testCheckSettingDeletedModel() {
    writeToBuildFile(TestFile.CHECK_SETTING_DELETED_MODEL)
    val buildModel = gradleBuildModel

    // Delete the property and attempt to set it again.
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      propertyModel.delete()
      propertyModel.setValue("New Value")

      verifyPropertyModel(propertyModel, STRING_TYPE, "New Value", STRING, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.CHECK_SETTING_DELETED_MODEL_EXPECTED)

    // Check this is still the case after a reparse.
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "New Value", STRING, REGULAR, 0)
    }

    // Check prop2
    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Other Value", STRING, REGULAR, 0)
    }
  }

  @Test
  fun testEmptyProperty() {
    val text = ""
    writeToBuildFile(text)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    val model = extModel.findProperty("prop")
    assertEquals(NONE, model.valueType)
    assertEquals(REGULAR, model.propertyType)
    assertEquals(null, model.getValue(STRING_TYPE))
    assertEquals(null, model.getValue(BOOLEAN_TYPE))
    assertEquals(null, model.getValue(INTEGER_TYPE))
    assertEquals(null, model.getValue(MAP_TYPE))
    assertEquals(null, model.getValue(LIST_TYPE))
    assertEquals("prop", model.name)
    assertEquals("ext.prop", model.fullyQualifiedName)
    assertEquals(buildModel.virtualFile, model.gradleFile)

    assertEquals(null, model.getRawValue(STRING_TYPE))
    assertSize(0, model.dependencies)
  }

  @Test
  fun testDeletePropertyInList() {
    writeToBuildFile(TestFile.DELETE_PROPERTY_IN_LIST)

    val buildModel = gradleBuildModel
    verifyDeletePropertyInList(buildModel)
  }

  @Test
  fun testDeleteArrayExpressionPropertyInList() {
    arrayExpressionSyntaxTestIsIrrelevantForGroovy()
    writeToBuildFile(TestFile.DELETE_ARRAY_EXPRESSION_PROPERTY_IN_LIST)

    val buildModel = gradleBuildModel
    verifyDeletePropertyInList(buildModel)
  }

  @Test
  fun testCreateNewEmptyMapValue() {
    val text = ""
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)

      // Make it an empty map.
      propertyModel.convertToEmptyMap()
      assertEquals(MAP, propertyModel.valueType)
      assertSize(0, propertyModel.getValue(MAP_TYPE)!!.entries)
    }

    applyChangesAndReparse(buildModel)
    // TODO(b/148198247): we need type decorators on the empty map in KotlinScript
    verifyFileContents(myBuildFile, TestFile.CREATE_NEW_EMPTY_MAP_VALUE_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      assertSize(0, propertyModel.getValue(MAP_TYPE)!!.entries)
    }
  }

  @Test
  fun testAddMapValueToString() {
    writeToBuildFile(TestFile.ADD_MAP_VALUE_TO_STRING)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      try {
        propertyModel.getMapValue("key")
        fail("Exception should have been thrown!")
      }
      catch (e: IllegalStateException) {
        // Expected.
      }
    }
  }

  @Test
  fun testSetNewValueInMap() {
    writeToBuildFile(TestFile.SET_NEW_VALUE_IN_MAP)

    val buildModel = gradleBuildModel
    verifySetNewValueInMap(buildModel)
  }

  @Test
  fun testSetNewValueInMapForKTSArrayExpression() {
    arrayExpressionSyntaxTestIsIrrelevantForGroovy()
    writeToBuildFile(TestFile.SET_NEW_VALUE_IN_MAP_FOR_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel
    verifySetNewValueInMap(buildModel)
  }

  @Test
  fun testSetNewValueInEmptyMap() {
    writeToBuildFile(TestFile.SET_NEW_VALUE_IN_EMPTY_MAP)
    val buildModel = gradleBuildModel

    verifySetNewValueInEmptyMap(buildModel)
  }

  @Test
  fun testSetNewValueInEmptyMapForKTSArrayExpression() {
    arrayExpressionSyntaxTestIsIrrelevantForGroovy()
    writeToBuildFile(TestFile.SET_NEW_VALUE_IN_EMPTY_MAP_FOR_KTS_ARRAY_EXPRESSION)
    val buildModel = gradleBuildModel

    verifySetNewValueInEmptyMap(buildModel)
  }

  @Test
  fun testDeletePropertyInMap() {
    writeToBuildFile(TestFile.DELETE_PROPERTY_IN_MAP)

    val buildModel = gradleBuildModel

    verifyDeletePropertyInMap(buildModel, TestFile.DELETE_PROPERTY_IN_MAP_EXPECTED)
  }

  @Test
  fun testDeletePropertyInMapForKTSArrayExpression() {
    arrayExpressionSyntaxTestIsIrrelevantForGroovy()
    writeToBuildFile(TestFile.DELETE_PROPERTY_IN_MAP_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyDeletePropertyInMap(buildModel, TestFile.DELETE_PROPERTY_IN_MAP_FOR_KTS_ARRAY_EXPRESSION_EXPECTED)
  }

  @Test
  fun testDeleteMapItemToAndSetFromEmpty() {
    writeToBuildFile(TestFile.DELETE_MAP_ITEM_TO_AND_SET_FROM_EMPTY)

    val buildModel = gradleBuildModel

    verifyDeleteMapItemToAndSetFromEmpty(buildModel)
  }

  @Test
  fun testDeleteMapItemToAndSetFromEmptyForKTSArrayExpression() {
    arrayExpressionSyntaxTestIsIrrelevantForGroovy()
    writeToBuildFile(TestFile.DELETE_MAP_ITEM_TO_AND_SET_FROM_EMPTY_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyDeleteMapItemToAndSetFromEmpty(buildModel)
  }

  @Test
  fun testSetMapValueToLiteral() {
    writeToBuildFile(TestFile.SET_MAP_VALUE_TO_LITERAL)

    val buildModel = gradleBuildModel

    verifySetMapValueToLiteralForKTSArrayExpression(buildModel)
  }

  @Test
  fun testSetMapValueToLiteralForKTSArrayExpression() {
    arrayExpressionSyntaxTestIsIrrelevantForGroovy()
    writeToBuildFile(TestFile.SET_MAP_VALUE_TO_LITERAL_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifySetMapValueToLiteralForKTSArrayExpression(buildModel)
  }

  @Test
  fun testDeleteToEmptyMap() {
    writeToBuildFile(TestFile.DELETE_TO_EMPTY_MAP)

    val buildModel = gradleBuildModel

    verifyDeleteToEmptyMap(buildModel)
  }

  @Test
  fun testDeleteToEmptyMapForKTSArrayExpression() {
    arrayExpressionSyntaxTestIsIrrelevantForGroovy()
    writeToBuildFile(TestFile.DELETE_TO_EMPTY_MAP_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyDeleteToEmptyMap(buildModel)
  }

  @Test
  fun testAddExistingMapProperty() {
    writeToBuildFile(TestFile.ADD_EXISTING_MAP_PROPERTY)

    val buildModel = gradleBuildModel

    verifyAddExistingMapProperty(buildModel)
  }

  @Test
  fun testAddExistingMapPropertyForKTSArrayExpression() {
    arrayExpressionSyntaxTestIsIrrelevantForGroovy()
    writeToBuildFile(TestFile.ADD_EXISTING_MAP_PROPERTY_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyAddExistingMapProperty(buildModel)
  }

  @Test
  fun testDeleteMapProperty() {
    writeToBuildFile(TestFile.DELETE_MAP_PROPERTY)

    val buildModel = gradleBuildModel

    verifyDeleteMapProperty(buildModel)
  }

  @Test
  fun testDeleteMapPropertyForKTSArrayExpression() {
    arrayExpressionSyntaxTestIsIrrelevantForGroovy()
    writeToBuildFile(TestFile.DELETE_MAP_PROPERTY_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyDeleteMapProperty(buildModel)
  }

  @Test
  fun testDeleteMapVariable() {
    writeToBuildFile(TestFile.DELETE_MAP_VARIABLE)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "map", REFERENCE, REGULAR, 1)

      val mapModel = propertyModel.dependencies[0]!!
      assertEquals(MAP, mapModel.valueType)
      assertEquals(VARIABLE, mapModel.propertyType)
      assertSize(1, mapModel.getValue(MAP_TYPE)!!.entries)

      // Delete the map model.
      mapModel.delete()
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.DELETE_MAP_VARIABLE_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "map", REFERENCE, REGULAR, 0)
    }
  }

  @Test
  fun testDeleteEmptyMap() {
    writeToBuildFile(TestFile.DELETE_EMPTY_MAP)

    val buildModel = gradleBuildModel

    verifyDeleteEmptyMap(buildModel)
  }

  @Test
  fun testDeleteEmptyMapForKTSArrayExpression() {
    arrayExpressionSyntaxTestIsIrrelevantForGroovy()
    writeToBuildFile(TestFile.DELETE_EMPTY_MAP_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyDeleteEmptyMap(buildModel)
  }

  @Test
  fun testSetLiteralToMapValue() {
    writeToBuildFile(TestFile.SET_LITERAL_TO_MAP_VALUE)

    val buildModel = gradleBuildModel

    verifySetLiteralToMapValue(buildModel)
  }

  @Test
  fun testSetLiteralToMapValueForKTSArrayExpression() {
    arrayExpressionSyntaxTestIsIrrelevantForGroovy()
    writeToBuildFile(TestFile.SET_LITERAL_TO_MAP_VALUE_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifySetLiteralToMapValue(buildModel)
  }

  @Test
  fun testParseMapInMap() {
    writeToBuildFile(TestFile.PARSE_MAP_IN_MAP)

    val buildModel = gradleBuildModel

    verifyParseMapInMap(buildModel)
  }

  @Test
  fun testParseMapInMapForKTSArrayExpression() {
    arrayExpressionSyntaxTestIsIrrelevantForGroovy()
    writeToBuildFile(TestFile.PARSE_MAP_IN_MAP_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyParseMapInMap(buildModel)
  }

  @Test
  fun testMapsInMap() {
    writeToBuildFile(TestFile.MAPS_IN_MAP)

    val buildModel = gradleBuildModel

    verifyMapsInMap(buildModel, TestFile.MAPS_IN_MAP_EXPECTED)
  }

  @Test
  fun testMapsInMapForKTSArrayExpression() {
    arrayExpressionSyntaxTestIsIrrelevantForGroovy()
    writeToBuildFile(TestFile.MAPS_IN_MAP_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyMapsInMap(buildModel, TestFile.MAPS_IN_MAP_FOR_KTS_ARRAY_EXPRESSION_EXPECTED)
  }

  @Test
  fun testMapOrder() {
    writeToBuildFile(TestFile.MAP_ORDER)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(3, map.entries)

      assertThat(map.keys.toList(), equalTo(listOf("key1", "key3", "key2")))
    }
  }

  @Test
  fun testSetMapInMap() {
    writeToBuildFile(TestFile.SET_MAP_IN_MAP)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)
      // Try to set a new map value.
      propertyModel.getValue(MAP_TYPE)!!["key1"]!!.convertToEmptyMap().getMapValue("War").setValue("Death")
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.SET_MAP_IN_MAP_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)
      val innerProperty = map["key1"]!!
      assertEquals(MAP, innerProperty.valueType)
      val innerMap = innerProperty.getValue(MAP_TYPE)!!
      assertSize(1, innerMap.entries)
      verifyPropertyModel(innerMap["War"], STRING_TYPE, "Death", STRING, DERIVED, 0)
    }
  }

  @Test
  fun testCreateNewEmptyList() {
    val text = ""
    writeToBuildFile(text)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)

      propertyModel.convertToEmptyList()
      assertEquals(LIST, propertyModel.valueType)
      val list = propertyModel.getValue(LIST_TYPE)
      assertSize(0, list)
    }

    applyChangesAndReparse(buildModel)
    // TODO(b/148198247): we need type decorators on the empty list in KotlinScript
    verifyFileContents(myBuildFile, TestFile.CREATE_NEW_EMPTY_LIST_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(LIST, propertyModel.valueType)
      val list = propertyModel.getValue(LIST_TYPE)
      assertSize(0, list)
    }
  }

  @Test
  fun testConvertToEmptyList() {
    writeToBuildFile(TestFile.CONVERT_TO_EMPTY_LIST)

    val buildModel = gradleBuildModel

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstModel, INTEGER_TYPE, 25, INTEGER, REGULAR, 0)
      firstModel.convertToEmptyList()
      assertEquals(LIST, firstModel.valueType)
      val firstList = firstModel.getValue(LIST_TYPE)
      assertSize(0, firstList)

      val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)
      secondModel.convertToEmptyList()
      assertEquals(LIST, secondModel.valueType)
      val secondList = secondModel.getValue(LIST_TYPE)
      assertSize(0, secondList)

      val thirdModel = buildModel.ext().findProperty("prop3")
      thirdModel.convertToEmptyList()
      assertEquals(LIST, thirdModel.valueType)
      val thirdList = thirdModel.getValue(LIST_TYPE)
      assertSize(0, thirdList)
    }

    applyChangesAndReparse(buildModel)
    // TODO(b/148198247): we need type decorators on the empty map in KotlinScript
    verifyFileContents(myBuildFile, TestFile.CONVERT_TO_EMPTY_LIST_EXPECTED)

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      assertEquals(LIST, firstModel.valueType)
      val firstList = firstModel.getValue(LIST_TYPE)
      assertSize(0, firstList)

      val secondModel = buildModel.ext().findProperty("prop2")
      assertEquals(LIST, secondModel.valueType)
      val secondList = secondModel.getValue(LIST_TYPE)
      assertSize(0, secondList)

      val thirdModel = buildModel.ext().findProperty("prop3")
      assertEquals(LIST, thirdModel.valueType)
      val thirdList = thirdModel.getValue(LIST_TYPE)
      assertSize(0, thirdList)
    }
  }

  @Test
  fun testAddToNoneList() {
    writeToBuildFile(TestFile.ADD_TO_NONE_LIST)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0)

      try {
        propertyModel.addListValue().setValue("True")
        fail()
      }
      catch (e: IllegalStateException) {
        // Expected
      }

      try {
        propertyModel.addListValueAt(23).setValue(72)
        fail()
      }
      catch (e: IllegalStateException) {
        // Expected
      }
    }
  }

  @Test
  fun testAddOutOfBounds() {
    writeToBuildFile(TestFile.ADD_OUT_OF_BOUNDS)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, 2, 3, 4, 5, 6, "hello"), REGULAR, 0)

      try {
        propertyModel.addListValueAt(82).setValue(true)
        fail()
      }
      catch (e: IndexOutOfBoundsException) {
        // Expected
      }
    }
  }

  @Test
  fun testSetListInMap() {
    writeToBuildFile(TestFile.SET_LIST_IN_MAP)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(3, map.entries)
      verifyPropertyModel(map["key"], STRING_TYPE, "val", STRING, DERIVED, 0)
      verifyPropertyModel(map["key1"], STRING_TYPE, "val", STRING, DERIVED, 0)
      verifyPropertyModel(map["key2"], STRING_TYPE, "val", STRING, DERIVED, 0)
      map["key1"]!!.convertToEmptyList().addListValue().setValue(true)
      verifyListProperty(map["key1"], listOf(true), DERIVED, 0)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.SET_LIST_IN_MAP_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(3, map.entries)
      verifyPropertyModel(map["key"], STRING_TYPE, "val", STRING, DERIVED, 0)
      verifyListProperty(map["key1"], listOf(true), DERIVED, 0)
      verifyPropertyModel(map["key2"], STRING_TYPE, "val", STRING, DERIVED, 0)
    }
  }

  @Test
  fun testSetToListValues() {
    writeToBuildFile(TestFile.SET_TO_LIST_VALUES)

    val buildModel = gradleBuildModel

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(firstModel, INTEGER_TYPE, 5, INTEGER, REGULAR, 0)
      firstModel.convertToEmptyList().addListValue().setValue("5")
      verifyListProperty(firstModel, listOf("5"), REGULAR, 0)

      val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, STRING_TYPE, "var1", REFERENCE, REGULAR, 1)
      val varModel = secondModel.dependencies[0]!!
      verifyPropertyModel(varModel, STRING_TYPE, "hello", STRING, VARIABLE, 0)
      varModel.convertToEmptyList().addListValue().setValue("goodbye")
      secondModel.setValue(ReferenceTo("var1[0]"))
      verifyPropertyModel(secondModel, STRING_TYPE, "var1[0]", REFERENCE, REGULAR, 1)
      val depModel = secondModel.dependencies[0]!!
      verifyPropertyModel(depModel, STRING_TYPE, "goodbye", STRING, DERIVED, 0)

      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(thirdModel, STRING_TYPE, "goodbye", STRING, REGULAR, 1)
      thirdModel.convertToEmptyList().addListValue().setValue(ReferenceTo("prop2"))
      assertEquals(LIST, thirdModel.valueType)
      val thirdList = thirdModel.getValue(LIST_TYPE)!!
      assertSize(1, thirdList)
      verifyPropertyModel(thirdList[0], STRING_TYPE, "prop2", REFERENCE, DERIVED, 1)

      val fourthModel = buildModel.ext().findProperty("prop4")
      assertEquals(MAP, fourthModel.valueType)
      val map = fourthModel.getValue(MAP_TYPE)!!
      assertSize(2, map.entries)
      verifyPropertyModel(map["key"], STRING_TYPE, "val", STRING, DERIVED, 0)
      verifyPropertyModel(map["key1"], BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)
      map["key"]!!.convertToEmptyList().addListValue().setValue("we are in")
      verifyListProperty(map["key"], listOf("we are in"), DERIVED, 0)

      val fifthModel = buildModel.ext().findProperty("prop5")
      verifyListProperty(fifthModel, listOf("val"), REGULAR, 0)
      fifthModel.convertToEmptyList().addListValue().setValue("good")
      verifyListProperty(fifthModel, listOf("good"), REGULAR, 0)

      val sixthModel = buildModel.ext().findProperty("prop6")
      assertEquals(MAP, sixthModel.valueType)
      sixthModel.convertToEmptyList().addListValue().setValue(true)
      verifyListProperty(sixthModel, listOf(true), REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(firstModel, listOf("5"), REGULAR, 0)

      // TODO: Order of statements is wrong so this model does not get correctly parsed.
      /*val secondModel = buildModel.ext().findProperty("prop2")
verifyPropertyModel(secondModel, STRING_TYPE, "var1[0]", REFERENCE, REGULAR, 1)
val depModel = secondModel.dependencies[0]!!
verifyPropertyModel(depModel, STRING_TYPE, "goodbye", STRING, DERIVED, 0)*/

      val thirdModel = buildModel.ext().findProperty("prop3")
      assertEquals(LIST, thirdModel.valueType)
      val thirdList = thirdModel.getValue(LIST_TYPE)!!
      assertSize(1, thirdList)
      verifyPropertyModel(thirdList[0], STRING_TYPE, "prop2", REFERENCE, DERIVED, 1)

      val fourthModel = buildModel.ext().findProperty("prop4")
      assertEquals(MAP, fourthModel.valueType)
      val map = fourthModel.getValue(MAP_TYPE)!!
      verifyListProperty(map["key"], listOf("we are in"), DERIVED, 0)
      verifyPropertyModel(map["key1"], BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)

      val fifthModel = buildModel.ext().findProperty("prop5")
      verifyListProperty(fifthModel, listOf("good"), REGULAR, 0)

      val sixthModel = buildModel.ext().findProperty("prop6")
      verifyListProperty(sixthModel, listOf(true), REGULAR, 0)
    }
  }

  @Test
  fun testAddSingleElementToEmpty() {
    writeToBuildFile(TestFile.ADD_SINGLE_ELEMENT_TO_EMPTY)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0)
      propertyModel.convertToEmptyList().addListValue().setValue("Good")

      verifyListProperty(propertyModel, listOf("Good"), REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_SINGLE_ELEMENT_TO_EMPTY_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf("Good"), REGULAR, 0)
    }
  }

  @Test
  fun testAddToAndDeleteListFromEmpty() {
    writeToBuildFile(TestFile.ADD_TO_AND_DELETE_LIST_FROM_EMPTY)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(LIST, propertyModel.valueType)
      verifyListProperty(propertyModel, listOf(), REGULAR, 0)

      propertyModel.addListValue().setValue("3")
      propertyModel.addListValue().setValue("4")
      propertyModel.addListValueAt(0).setValue("1")
      propertyModel.addListValueAt(1).setValue("2")
      propertyModel.addListValueAt(4).setValue(5)
      propertyModel.addListValueAt(5).setValue(ReferenceTo("six"))

      val list = propertyModel.getValue(LIST_TYPE)!!
      assertSize(6, list)
      verifyPropertyModel(list[0], STRING_TYPE, "1", STRING, DERIVED, 0, "0", "ext.prop1[0]")
      verifyPropertyModel(list[1], STRING_TYPE, "2", STRING, DERIVED, 0, "1", "ext.prop1[1]")
      verifyPropertyModel(list[2], STRING_TYPE, "3", STRING, DERIVED, 0, "2", "ext.prop1[2]")
      verifyPropertyModel(list[3], STRING_TYPE, "4", STRING, DERIVED, 0, "3", "ext.prop1[3]")
      verifyPropertyModel(list[4], INTEGER_TYPE, 5, INTEGER, DERIVED, 0, "4", "ext.prop1[4]")
      verifyPropertyModel(list[5], STRING_TYPE, "six", REFERENCE, DERIVED, 1, "5", "ext.prop1[5]")
      verifyPropertyModel(list[5].dependencies[0], INTEGER_TYPE, 6, INTEGER, VARIABLE, 0, "six")

      // Delete some elements
      list[1].delete()
      list[3].delete()
      list[5].delete()

      val newList = propertyModel.getValue(LIST_TYPE)!!
      assertSize(3, newList)
      verifyPropertyModel(newList[0], STRING_TYPE, "1", STRING, DERIVED, 0, "0", "ext.prop1[0]")
      verifyPropertyModel(newList[1], STRING_TYPE, "3", STRING, DERIVED, 0, "1", "ext.prop1[1]")
      verifyPropertyModel(newList[2], INTEGER_TYPE, 5, INTEGER, DERIVED, 0, "2", "ext.prop1[2]")
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_TO_AND_DELETE_LIST_FROM_EMPTY_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(LIST, propertyModel.valueType)
      val newList = propertyModel.getValue(LIST_TYPE)!!
      assertSize(3, newList)
      verifyPropertyModel(newList[0], STRING_TYPE, "1", STRING, DERIVED, 0, "0", "ext.prop1[0]")
      verifyPropertyModel(newList[1], STRING_TYPE, "3", STRING, DERIVED, 0, "1", "ext.prop1[1]")
      verifyPropertyModel(newList[2], INTEGER_TYPE, 5, INTEGER, DERIVED, 0, "2", "ext.prop1[2]")
    }
  }

  @Test
  fun testAddAndRemoveFromNonLiteralList() {
    writeToBuildFile(TestFile.ADD_AND_REMOVE_FROM_NON_LITERAL_LIST)

    val buildModel = gradleBuildModel

    val quoteChar = if(isGroovy) "'" else "\""

    run {
      val proguardFiles = buildModel.android().defaultConfig().proguardFiles()
      verifyListProperty(proguardFiles, listOf("getDefaultProguardFile(${quoteChar}proguard-android.txt${quoteChar})", "proguard-rules2.txt"), DERIVED, 0)
      proguardFiles.addListValueAt(0).setValue("z.txt")
      proguardFiles.addListValueAt(2).setValue("proguard-rules.txt")
      verifyListProperty(
        proguardFiles,
        listOf("z.txt", "getDefaultProguardFile(${quoteChar}proguard-android.txt${quoteChar})", "proguard-rules.txt", "proguard-rules2.txt"),
        DERIVED,
        0
      )
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_AND_REMOVE_FROM_NON_LITERAL_LIST_EXPECTED)

    run {
      val proguardFiles = buildModel.android().defaultConfig().proguardFiles()
      verifyListProperty(
        proguardFiles,
        listOf("z.txt", "getDefaultProguardFile(${quoteChar}proguard-android.txt${quoteChar})", "proguard-rules.txt", "proguard-rules2.txt"),
        DERIVED,
        0
      )
    }
  }

  @Test
  fun testSetList() {
    writeToBuildFile(TestFile.SET_LIST)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, 2, 3), REGULAR, 0)
      // Set middle value
      propertyModel.getValue(LIST_TYPE)!![1].setValue(true)
      verifyListProperty(propertyModel, listOf(1, true, 3), REGULAR, 0)

      val secondModel = buildModel.ext().findProperty("prop2")
      verifyListProperty(secondModel, listOf("hellO"), REGULAR, 0)
      secondModel.setValue(77)
      verifyPropertyModel(secondModel, INTEGER_TYPE, 77, INTEGER, REGULAR, 0)

      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyListProperty(thirdModel, listOf(54), REGULAR, 0)
      thirdModel.setValue(ReferenceTo("prop1[1]"))
      verifyPropertyModel(thirdModel, STRING_TYPE, "prop1[1]", REFERENCE, REGULAR, 1)
      verifyPropertyModel(thirdModel.dependencies[0], BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.SET_LIST_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, true, 3), REGULAR, 0)

      val secondModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(secondModel, INTEGER_TYPE, 77, INTEGER, REGULAR, 0)

      // TODO: This is not currently parsed.
      val thirdModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(thirdModel, STRING_TYPE, "prop1[1]", REFERENCE, REGULAR, 1)
      verifyPropertyModel(thirdModel.dependencies[0], BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)
    }
  }

  @Test
  fun testAddMiddleOfList() {
    writeToBuildFile(TestFile.ADD_MIDDLE_OF_LIST)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, 4), REGULAR, 0)

      propertyModel.addListValueAt(1).setValue(ReferenceTo("var1"))
      propertyModel.addListValueAt(2).setValue(3)

      verifyListProperty(propertyModel, listOf(1, "2", 3, 4), REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_MIDDLE_OF_LIST_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, "2", 3, 4), REGULAR, 1)
    }
  }

  @Test
  fun testSetInMiddleOfList() {
    writeToBuildFile(TestFile.SET_IN_MIDDLE_OF_LIST)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, 2, "2", 4), REGULAR, 1)

      propertyModel.getValue(LIST_TYPE)!![1].setValue(ReferenceTo("var1"))
      propertyModel.getValue(LIST_TYPE)!![2].setValue(3)

      verifyListProperty(propertyModel, listOf(1, "2", 3, 4), REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.SET_IN_MIDDLE_OF_LIST_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, "2", 3, 4), REGULAR, 1)
    }
  }

  @Test
  fun testResolveAndSetVariablesInParentModule() {
    writeToBuildFile(TestFile.RESOLVE_AND_SET_VARIABLES_IN_PARENT_MODULE)
    writeToSubModuleBuildFile(TestFile.RESOLVE_VARIABLES_IN_PARENT_MODULE_SUB)
    writeToSettingsFile(subModuleSettingsText)

    val projectBuildModel = projectBuildModel
    val buildModel = projectBuildModel.getModuleBuildModel(mySubModule)!!

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      propertyModel.getValue(STRING_TYPE)
      verifyPropertyModel(propertyModel, STRING_TYPE, "greeting", REFERENCE, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "hello", STRING, REGULAR, 0)
      val otherModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(otherModel, STRING_TYPE, "hello world!", STRING, REGULAR, 1)


      propertyModel.dependencies[0].setValue("howdy")

      verifyPropertyModel(propertyModel, STRING_TYPE, "greeting", REFERENCE, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "howdy", STRING, REGULAR, 0)
      verifyPropertyModel(otherModel, STRING_TYPE, "howdy world!", STRING, REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(mySubModuleBuildFile, TestFile.RESOLVE_VARIABLES_IN_PARENT_MODULE_SUB)
    // the above applyChanges does not write the change to the parent, as it is the submodule's build model
    verifyFileContents(myBuildFile, TestFile.RESOLVE_AND_SET_VARIABLES_IN_PARENT_MODULE)

    applyChangesAndReparse(projectBuildModel)
    verifyFileContents(myBuildFile, TestFile.RESOLVE_AND_SET_VARIABLES_IN_PARENT_MODULE_EXPECTED)
    verifyFileContents(mySubModuleBuildFile, TestFile.RESOLVE_VARIABLES_IN_PARENT_MODULE_SUB)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      val otherModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "greeting", REFERENCE, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "howdy", STRING, REGULAR, 0)
      verifyPropertyModel(otherModel, STRING_TYPE, "howdy world!", STRING, REGULAR, 1)
    }
  }

  @Test
  fun testResolveVariablesInPropertiesFile() {
    val childProperties = "animal = lion"
    val parentProperties = "animal = meerkat"
    writeToBuildFile(TestFile.RESOLVE_VARIABLES_IN_PROPERTIES_FILE)
    writeToSubModuleBuildFile(TestFile.RESOLVE_VARIABLES_IN_PROPERTIES_FILE_SUB)
    writeToSettingsFile(subModuleSettingsText)
    writeToPropertiesFile(parentProperties)
    writeToSubModulePropertiesFile(childProperties)

    var buildModel = subModuleGradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello, rhino!", STRING, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "rhino", STRING, VARIABLE, 0)

      // Delete the dependency and try resolution again.
      propertyModel.dependencies[0].delete()

      verifyPropertyModel(propertyModel, STRING_TYPE, "hello, lion!", STRING, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "lion", STRING, PROPERTIES_FILE, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello, lion!", STRING, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "lion", STRING, PROPERTIES_FILE, 0)

      // Properties file can't be edited directly.
      writeToSubModulePropertiesFile("")
      // Applying changes and reparsing does not affect properties files, need to completely remake the build model.
      buildModel = subModuleGradleBuildModel
    }

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello, meerkat!", STRING, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "meerkat", STRING, PROPERTIES_FILE, 0)

      // Properties file can't be edited directly.
      writeToPropertiesFile("")
      // Applying changes and reparsing does not affect properties files, need to completely remake the build model.
      buildModel = subModuleGradleBuildModel
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello, penguin!", STRING, REGULAR, 1)
      verifyPropertyModel(propertyModel.dependencies[0], STRING_TYPE, "penguin", STRING, REGULAR, 0)

      propertyModel.dependencies[0].delete()

      verifyPropertyModel(propertyModel, STRING_TYPE, "hello, ${'$'}{animal}!", STRING, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello, ${'$'}{animal}!", STRING, REGULAR, 0)
    }
  }

  @Test
  fun testSetValueInMap() {
    writeToBuildFile(TestFile.SET_VALUE_IN_MAP)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      verifyPropertyModel(map["key1"], STRING_TYPE, "value", STRING, DERIVED, 0)
      verifyPropertyModel(map["key2"], STRING_TYPE, "val1", REFERENCE, DERIVED, 1)
      verifyPropertyModel(map["key3"], INTEGER_TYPE, 23, INTEGER, DERIVED, 0)
      verifyPropertyModel(map["key4"], BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)

      propertyModel.getMapValue("key1").setValue(ReferenceTo("otherVal"))
      propertyModel.getMapValue("key2").setValue("newValue")
      propertyModel.getMapValue("key3").setValue(false)
      propertyModel.getMapValue("key4").setValue(32)
      propertyModel.getMapValue("newKey").setValue("meerkats")

      assertEquals(MAP, propertyModel.valueType)
      val newMap = propertyModel.getValue(MAP_TYPE)!!
      verifyPropertyModel(newMap["key1"], STRING_TYPE, "otherVal", REFERENCE, DERIVED, 1)
      verifyPropertyModel(newMap["key2"], STRING_TYPE, "newValue", STRING, DERIVED, 0)
      verifyPropertyModel(newMap["key3"], BOOLEAN_TYPE, false, BOOLEAN, DERIVED, 0)
      verifyPropertyModel(newMap["key4"], INTEGER_TYPE, 32, INTEGER, DERIVED, 0)
      verifyPropertyModel(newMap["newKey"], STRING_TYPE, "meerkats", STRING, DERIVED, 0)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.SET_VALUE_IN_MAP_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      verifyPropertyModel(map["key1"], STRING_TYPE, "otherVal", REFERENCE, DERIVED, 1)
      verifyPropertyModel(map["key2"], STRING_TYPE, "newValue", STRING, DERIVED, 0)
      verifyPropertyModel(map["key3"], BOOLEAN_TYPE, false, BOOLEAN, DERIVED, 0)
      verifyPropertyModel(map["key4"], INTEGER_TYPE, 32, INTEGER, DERIVED, 0)
      verifyPropertyModel(map["newKey"], STRING_TYPE, "meerkats", STRING, DERIVED, 0)
    }
  }

  @Test
  fun testSetMapValueOnNoneMap() {
    writeToBuildFile(TestFile.SET_MAP_VALUE_ON_NONE_MAP)

    val buildModel = gradleBuildModel

    run {
      val firstModel = buildModel.ext().findProperty("prop1")
      try {
        firstModel.getMapValue("value1").setValue("newValue")
        fail()
      }
      catch (e: IllegalStateException) {
        // Expected
      }

      val secondModel = buildModel.ext().findProperty("prop2")
      try {
        secondModel.getMapValue("hello").setValue("goodbye")
        fail()
      }
      catch (e: IllegalStateException) {
        // Expected
      }

      val thirdModel = buildModel.ext().findProperty("prop3")
      try {
        thirdModel.getMapValue("key").setValue(0)
        fail()
      }
      catch (e: IllegalStateException) {
        // Expected
      }
    }
  }

  @Test
  fun testOuterScopeVariablesResolved() {
    writeToBuildFile(TestFile.OUTER_SCOPE_VARIABLES_RESOLVED)

    val buildModel = gradleBuildModel

    run {
      val defaultConfig = buildModel.android().defaultConfig()
      verifyPropertyModel(defaultConfig.minSdkVersion(), INTEGER_TYPE, 12, INTEGER, REGULAR, 1)
      verifyPropertyModel(defaultConfig.targetSdkVersion(), INTEGER_TYPE, 15, INTEGER, REGULAR, 1)

      // Check that we can edit them.
      defaultConfig.minSdkVersion().resultModel.setValue(18)
      defaultConfig.targetSdkVersion().resultModel.setValue(21)

      verifyPropertyModel(defaultConfig.minSdkVersion(), INTEGER_TYPE, 18, INTEGER, REGULAR, 1)
      verifyPropertyModel(defaultConfig.targetSdkVersion(), INTEGER_TYPE, 21, INTEGER, REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.OUTER_SCOPE_VARIABLES_RESOLVED_EXPECTED)

    run {
      val defaultConfig = buildModel.android().defaultConfig()
      verifyPropertyModel(defaultConfig.minSdkVersion(), INTEGER_TYPE, 18, INTEGER, REGULAR, 1)
      verifyPropertyModel(defaultConfig.targetSdkVersion(), INTEGER_TYPE, 21, INTEGER, REGULAR, 1)
    }
  }

  @Test
  fun testInScopeElement() {
    assumeTrue("no ext block in KotlinScript", !isKotlinScript) // TODO(b/154902406)
    val childProperties = "prop3 = chickadee"
    val parentProperties = "prop4 = ferret\nnested.prop5 = narwhal"
    writeToBuildFile(TestFile.IN_SCOPE_ELEMENT)
    writeToSubModuleBuildFile(TestFile.IN_SCOPE_ELEMENT_SUB)
    writeToSettingsFile(subModuleSettingsText)
    writeToPropertiesFile(parentProperties)
    writeToSubModulePropertiesFile(childProperties)

    val buildModel = subModuleGradleBuildModel

    run {
      val defaultConfig = buildModel.android().defaultConfig()
      val properties = defaultConfig.inScopeProperties
      assertEquals(7, properties.entries.size)

      // Check all the properties that we expect are present.
      verifyPropertyModel(properties["var3"], STRING_TYPE, "goldeneye", STRING, VARIABLE, 0)
      verifyPropertyModel(properties["var4"], STRING_TYPE, "wallaby", STRING, VARIABLE, 0)
      verifyPropertyModel(properties["var5"], STRING_TYPE, "curlew", STRING, VARIABLE, 0)
      verifyPropertyModel(properties["prop1"], STRING_TYPE, "baboon", STRING, REGULAR, 0)
      verifyPropertyModel(properties["prop2"], STRING_TYPE, "kite", STRING, REGULAR, 0)
      verifyPropertyModel(properties["prop3"], STRING_TYPE, "chickadee", STRING, PROPERTIES_FILE, 0)
      verifyPropertyModel(properties["prop4"], STRING_TYPE, "ferret", STRING, PROPERTIES_FILE, 0)
    }

    run {
      val properties = buildModel.ext().inScopeProperties
      assertEquals(6, properties.entries.size)
      verifyPropertyModel(properties["prop1"], STRING_TYPE, "baboon", STRING, REGULAR, 0)
      verifyPropertyModel(properties["prop2"], STRING_TYPE, "kite", STRING, REGULAR, 0)
      verifyPropertyModel(properties["prop3"], STRING_TYPE, "chickadee", STRING, PROPERTIES_FILE, 0)
      verifyPropertyModel(properties["prop4"], STRING_TYPE, "ferret", STRING, PROPERTIES_FILE, 0)
      verifyPropertyModel(properties["var6"], STRING_TYPE, "swan", STRING, VARIABLE, 0)
      // TODO: Should not be visible, this needs line number support to correctly hide itself.
      verifyPropertyModel(properties["var3"], STRING_TYPE, "goldeneye", STRING, VARIABLE, 0)
    }
  }

  @Test
  fun testVariablesFromNestedApply() {
    val b = writeToNewProjectFile("b", TestFile.VARIABLES_FROM_NESTED_APPLY_APPLIED_FILE_ONE)
    val a = writeToNewProjectFile("a", TestFile.VARIABLES_FROM_NESTED_APPLY_APPLIED_FILE_TWO)
    writeToBuildFile(TestFile.VARIABLES_FROM_NESTED_APPLY)

    val buildModel = gradleBuildModel

    run {
      val properties = buildModel.ext().inScopeProperties
      assertSize(5, properties.values)
      verifyPropertyModel(properties["prop2"], BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "prop2", "ext.prop2")
      verifyFilePathsAreEqual(myProjectBasePath.findChild(b)!!, properties["prop2"]!!.gradleFile)
      verifyListProperty(properties["prop1"], listOf("var1", "var2", "var3"), false)
      verifyListProperty(properties["prop1"], listOf("1", true, 1), REGULAR, 3)
      verifyFilePathsAreEqual(myProjectBasePath.findChild(b)!!, properties["prop1"]!!.gradleFile)
      verifyPropertyModel(properties["prop4"], STRING_TYPE, "prop1[0]", REFERENCE, REGULAR, 1, "prop4", "ext.prop4")
      verifyFilePathsAreEqual(myProjectBasePath.findChild(myBuildFile.name)!!, properties["prop4"]!!.gradleFile)
      verifyPropertyModel(properties["prop3"], STRING_TYPE, "hello", STRING, REGULAR, 0, "prop3", "ext.prop3")
      verifyFilePathsAreEqual(myProjectBasePath.findChild(a)!!, properties["prop3"]!!.gradleFile)
      verifyPropertyModel(properties["prop5"], INTEGER_TYPE, 5, INTEGER, REGULAR, 0, "prop5", "ext.prop5")

      // Check we can actually make changes to all the files.
      properties["prop5"]!!.setValue(ReferenceTo("prop2"))
      properties["prop1"]!!.getValue(LIST_TYPE)!![1].dependencies[0].setValue(false)
      properties["prop1"]!!.getValue(LIST_TYPE)!![0].setValue(2)
      properties["prop2"]!!.setValue("true")

      verifyPropertyModel(properties["prop2"], STRING_TYPE, "true", STRING, REGULAR, 0, "prop2", "ext.prop2")
      verifyFilePathsAreEqual(myProjectBasePath.findChild(b)!!, properties["prop2"]!!.gradleFile)
      verifyListProperty(properties["prop1"], listOf(2, "var2", "var3"), false)
      verifyListProperty(properties["prop1"], listOf(2, false, 1), REGULAR, 2)
      verifyFilePathsAreEqual(myProjectBasePath.findChild(b)!!, properties["prop1"]!!.gradleFile)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myProjectBasePath.findChild(a)!!, TestFile.VARIABLES_FROM_NESTED_APPLY_APPLIED_FILE_TWO)
    verifyFileContents(myProjectBasePath.findChild(b)!!, TestFile.VARIABLES_FROM_NESTED_APPLY_APPLIED_FILE_ONE_EXPECTED)
    verifyFileContents(myBuildFile, TestFile.VARIABLES_FROM_NESTED_APPLY_EXPECTED)

    run {
      val properties = buildModel.ext().inScopeProperties
      verifyPropertyModel(properties["prop2"], STRING_TYPE, "true", STRING, REGULAR, 0, "prop2", "ext.prop2")
      verifyFilePathsAreEqual(myProjectBasePath.findChild(b)!!, properties["prop2"]!!.gradleFile)
      verifyListProperty(properties["prop1"], listOf(2, "var2", "var3"), false)
      verifyListProperty(properties["prop1"], listOf(2, false, 1), REGULAR, 2)
      verifyFilePathsAreEqual(myProjectBasePath.findChild(b)!!, properties["prop1"]!!.gradleFile)
      verifyPropertyModel(properties["prop4"], STRING_TYPE, "prop1[0]", REFERENCE, REGULAR, 1, "prop4", "ext.prop4")
      verifyFilePathsAreEqual(myProjectBasePath.findChild(myBuildFile.name)!!, properties["prop4"]!!.gradleFile)
      verifyPropertyModel(properties["prop3"], STRING_TYPE, "hello", STRING, REGULAR, 0, "prop3", "ext.prop3")
      verifyFilePathsAreEqual(myProjectBasePath.findChild(a)!!, properties["prop3"]!!.gradleFile)
    }
  }

  @Test
  fun testApplicationCycle() {
    writeToNewProjectFile("a", TestFile.APPLICATION_CYCLE_APPLIED)
    writeToBuildFile(TestFile.APPLICATION_CYCLE)

    // Make sure we don't blow up.
    val buildModel = gradleBuildModel

    // Make sure that we have detected the circularity somewhere.
    val circularApplications = gradleBuildModel.notifications
      .flatMap { e -> e.component2().filterIsInstance(CircularApplication::class.java) }
    assertTrue(circularApplications.isNotEmpty())

    // Somewhat arbitrary assertion about the state of the parse after the circularity has been detected.
    run {
      val properties = buildModel.ext().inScopeProperties
      assertSize(2, properties.values)
    }
  }

  @Test
  fun testVariablesFromApply() {
    val vars = writeToNewProjectFile("vars", TestFile.VARIABLES_FROM_APPLY_APPLIED)
    writeToBuildFile(TestFile.VARIABLES_FROM_APPLY)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop4")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Hello : Hello world! : true", STRING, REGULAR, 3)

      val deps = propertyModel.dependencies
      verifyPropertyModel(deps[0], STRING_TYPE, "var2", REFERENCE, REGULAR, 1)
      verifyPropertyModel(deps[1], STRING_TYPE, "Hello world!", STRING, REGULAR, 1)
      verifyPropertyModel(deps[2], STRING_TYPE, "var3", REFERENCE, REGULAR, 1)

      // Lets delete one of the variables
      verifyPropertyModel(deps[0].dependencies[0], STRING_TYPE, "var1", REFERENCE, VARIABLE, 1)
      deps[0].dependencies[0].delete()
      // And edit one of the properties
      deps[0].setValue(72)
      verifyPropertyModel(propertyModel, STRING_TYPE, "72 : 72 world! : true", STRING, REGULAR, 3)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.VARIABLES_FROM_APPLY)
    verifyFileContents(myProjectBasePath.findChild(vars)!!, TestFile.VARIABLES_FROM_APPLY_APPLIED_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop4")
      verifyPropertyModel(propertyModel, STRING_TYPE, "72 : 72 world! : true", STRING, REGULAR, 3)
    }
  }

  @Test
  fun testAddRemoveReferenceValues() {
    writeToBuildFile(TestFile.ADD_REMOVE_REFERENCE_VALUES)

    val buildModel = gradleBuildModel

    run {
      val extModel = buildModel.ext()
      val propertyModel = extModel.findProperty("propList")
      verifyListProperty(propertyModel, listOf("1", "2", "3", "2", "2nd"), REGULAR, 4)
      propertyModel.toList()!![0].setValue(ReferenceTo("propC"))
      verifyListProperty(propertyModel, listOf("3", "2", "3", "2", "2nd"), REGULAR, 5)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_REMOVE_REFERENCE_VALUES_EXPECTED)

    run {
      val extModel = buildModel.ext()
      val propertyModel = extModel.findProperty("propList")
      verifyListProperty(propertyModel, listOf("3", "2", "3", "2", "2nd"), REGULAR, 5)
    }
  }

  @Test
  fun testRename() {
    writeToBuildFile(TestFile.RENAME)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello ${'$'}{var2}", STRING, REGULAR, 1, "prop1", "ext.prop1")
      val varModel = propertyModel.dependencies[0]
      verifyPropertyModel(varModel, STRING_TYPE, "hello", STRING, VARIABLE, 0, "var1")

      // Rename the properties.
      if (isGroovy) {
        propertyModel.rename("prop2")
      }
      else {
        propertyModel.rename(listOf("ext", "prop2"))
      }
      varModel.rename("var2")

      verifyPropertyModel(propertyModel, STRING_TYPE, "${'$'}{var1} hello", STRING, REGULAR, 1, "prop2", "ext.prop2")
      verifyPropertyModel(varModel, STRING_TYPE, "hello", STRING, VARIABLE, 0, "var2")
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.RENAME_EXPECTED)

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      val varModel = propertyModel.dependencies[0]
      verifyPropertyModel(propertyModel, STRING_TYPE, "${'$'}{var1} hello", STRING, REGULAR, 1, "prop2", "ext.prop2")
      verifyPropertyModel(varModel, STRING_TYPE, "hello", STRING, VARIABLE, 0, "var2")
    }
  }

  @Test
  fun testRenameMapPropertyAndKeys() {
    writeToBuildFile(TestFile.RENAME_MAP_PROPERTY_AND_KEYS)

    val buildModel = gradleBuildModel

    fun findVariable(name: String): GradlePropertyModel {
      return when {
        isGroovy -> buildModel.ext().findProperty(name)
        else -> buildModel.declaredProperties.find { it.name == name }!!
      }
    }

    run {
      val firstMapModel = findVariable("map1")
      verifyMapProperty(firstMapModel, mapOf("key1" to "a", "key2" to "b", "key3" to "c"), "map1")
      val secondMapModel = buildModel.ext().findProperty("map2")
      verifyMapProperty(secondMapModel, mapOf("key4" to 4), "map2")

      // Rename the keys
      val firstKeyModel = firstMapModel.getMapValue("key2")
      verifyPropertyModel(firstKeyModel, STRING_TYPE, "b", STRING, DERIVED, 0, "key2")
      val secondKeyModel = secondMapModel.getMapValue("key4")
      verifyPropertyModel(secondKeyModel, INTEGER_TYPE, 4, INTEGER, DERIVED, 0, "key4")

      firstKeyModel.rename("newKey1")
      secondKeyModel.rename("newKey2")

      // Rename the maps
      firstMapModel.rename("newMap1")
      secondMapModel.rename("newMap2")

      verifyMapProperty(firstMapModel, mapOf("key1" to "a", "newKey1" to "b", "key3" to "c"), "newMap1")
      verifyMapProperty(secondMapModel, mapOf("newKey2" to 4), "newMap2")
      verifyPropertyModel(firstKeyModel, STRING_TYPE, "b", STRING, DERIVED, 0, "newKey1")
      verifyPropertyModel(secondKeyModel, INTEGER_TYPE, 4, INTEGER, DERIVED, 0, "newKey2")
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.RENAME_MAP_PROPERTY_AND_KEYS_EXPECTED)

    run {
      val firstMapModel = findVariable("newMap1")
      verifyMapProperty(firstMapModel, mapOf("key1" to "a", "newKey1" to "b", "key3" to "c"), "newMap1")
      val secondMapModel = buildModel.ext().findProperty("newMap2")
      verifyMapProperty(secondMapModel, mapOf("newKey2" to 4), "newMap2")

      // Rename the keys
      val firstKeyModel = firstMapModel.getMapValue("newKey1")
      verifyPropertyModel(firstKeyModel, STRING_TYPE, "b", STRING, DERIVED, 0, "newKey1")
      val secondKeyModel = secondMapModel.getMapValue("newKey2")
      verifyPropertyModel(secondKeyModel, INTEGER_TYPE, 4, INTEGER, DERIVED, 0, "newKey2")
    }
  }

  @Test
  fun testRenameListValueThrows() {
    writeToBuildFile(TestFile.RENAME_LIST_VALUE_THROWS)

    val buildModel = gradleBuildModel
    run {
      val firstListModel = when {
        isGroovy -> buildModel.ext().findProperty("list1")
        else -> buildModel.declaredProperties.find { it.name == "list1" }!!
      }
      verifyListProperty(firstListModel, listOf(1, 2, 3, 4), VARIABLE, 0, "list1")
      val secondListModel = buildModel.ext().findProperty("list2")
      verifyListProperty(secondListModel, listOf("a", "b", "c", "d"), REGULAR, 0, "list2")

      val listItem = secondListModel.getListValue("b")!!
      try {
        listItem.rename("listItemName")
        fail()
      }
      catch (e: UnsupportedOperationException) {
        // Expected
      }

      firstListModel.rename("varList")
      secondListModel.rename("propertyList")

      verifyListProperty(firstListModel, listOf(1, 2, 3, 4), VARIABLE, 0, "varList")
      verifyListProperty(secondListModel, listOf("a", "b", "c", "d"), REGULAR, 0, "propertyList")
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.RENAME_LIST_VALUE_THROWS_EXPECTED)

    run {
      val firstListModel = when {
        isGroovy -> buildModel.ext().findProperty("varList")
        else -> buildModel.declaredProperties.find { it.name == "varList" }!!
      }
      verifyListProperty(firstListModel, listOf(1, 2, 3, 4), VARIABLE, 0, "varList")
      val secondListModel = buildModel.ext().findProperty("propertyList")
      verifyListProperty(secondListModel, listOf("a", "b", "c", "d"), REGULAR, 0, "propertyList")
    }
  }

  @Test
  fun testGetDeclaredProperties() {
    writeToBuildFile(TestFile.GET_DECLARED_PROPERTIES)

    val buildModel = gradleBuildModel
    val topProperties = buildModel.declaredProperties
    val extProperties = buildModel.ext().declaredProperties
    val androidProperties = buildModel.android().declaredProperties
    val debugProperties = buildModel.android().buildTypes()[0].declaredProperties

    assertSize(1, extProperties)
    assertSize(3, androidProperties)
    assertSize(2, debugProperties)
    // TODO(b/148938436): I would have expected 5 here (3 for android, 1 for ext, and one for the top-level variable) but the implementation
    //  of declaredProperties on GradleBuildModelImpl has includeProperties = false, whereas on GradleDslBlockModel it uses
    //  includeProperties = true.
    assertSize(4, topProperties)

    verifyPropertyModel(extProperties[0], STRING_TYPE, "property", STRING, REGULAR, 0, "prop1")
    verifyPropertyModel(androidProperties[0], STRING_TYPE, "Spooky", STRING, VARIABLE, 0, "outerVar")
    verifyPropertyModel(androidProperties[1], STRING_TYPE, "sneaky", REFERENCE, VARIABLE, 0, "innerVar")
    verifyPropertyModel(androidProperties[2], BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, BuildTypeModelImpl.MINIFY_ENABLED)
    verifyPropertyModel(debugProperties[0], STRING_TYPE, "sneaky", REFERENCE, VARIABLE, 0, "innerVar")
    verifyPropertyModel(debugProperties[1], BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, BuildTypeModelImpl.MINIFY_ENABLED)
    verifyPropertyModel(topProperties[0], STRING_TYPE, "value", STRING, VARIABLE, 0, "topVar")
    verifyPropertyModel(topProperties[1], STRING_TYPE, "Spooky", STRING, VARIABLE, 0, "outerVar")
    verifyPropertyModel(topProperties[2], STRING_TYPE, "sneaky", REFERENCE, VARIABLE, 0, "innerVar")
    verifyPropertyModel(topProperties[3], STRING_TYPE, "property", STRING, REGULAR, 0, "prop1")
  }

  @Test
  fun testDeleteItemsFromList() {
    writeToBuildFile(TestFile.DELETE_ITEMS_FROM_LIST)

    val buildModel = gradleBuildModel
    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyListProperty(propertyModel, "ext.prop", listOf(1))
      val itemModel = propertyModel.toList()!![0]
      itemModel.delete()
    }

    applyChangesAndReparse(buildModel)
    // TODO(b/148198247): we need type decorators on the empty list in KotlinScript
    verifyFileContents(myBuildFile, TestFile.DELETE_ITEMS_FROM_LIST_EXPECTED)

    verifyListProperty(buildModel.ext().findProperty("prop"), "ext.prop", listOf())
  }

  @Test
  fun testDeleteListWithItems() {
    writeToBuildFile(TestFile.DELETE_LIST_WITH_ITEMS)

    val buildModel = gradleBuildModel
    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      propertyModel.delete()
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, "")

    assertMissingProperty(buildModel.ext().findProperty("prop"))
  }

  @Test
  fun testDeleteItemsInMap() {
    writeToBuildFile(TestFile.DELETE_ITEMS_IN_MAP)

    val buildModel = gradleBuildModel
    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyMapProperty(propertyModel, mapOf("key" to 1))
      val itemModel = propertyModel.toMap()!!["key"]!!
      itemModel.delete()
    }

    applyChangesAndReparse(buildModel)
    // TODO(b/148198247): we need type decorators on the empty map in KotlinScript
    verifyFileContents(myBuildFile, TestFile.DELETE_ITEMS_IN_MAP_EXPECTED)

    verifyMapProperty(buildModel.ext().findProperty("prop"), mapOf())
  }

  @Test
  fun testDeleteMapWithItems() {
    writeToBuildFile(TestFile.DELETE_MAP_WITH_ITEMS)

    val buildModel = gradleBuildModel
    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      propertyModel.delete()
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, "")

    assertMissingProperty(buildModel.ext().findProperty("prop"))
  }

  private fun runSetPropertyTest(fileName: TestFileName, type: PropertyType) {
    writeToBuildFile(fileName)

    val buildModel = gradleBuildModel

    fun findProperty(name: String): GradlePropertyModel {
      return when {
        isGroovy || type == REGULAR -> buildModel.ext().findProperty(name)
        else -> buildModel.declaredProperties.find { it.name == name }!!
      }
    }

    run {
      val propertyModel = findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "value", STRING, type, 0)
      val oldGradleFile = propertyModel.gradleFile

      val stringValue = "Hello world!"
      propertyModel.setValue(stringValue)
      verifyPropertyModel(propertyModel, STRING_TYPE, stringValue, STRING, type, 0)
      applyChangesAndReparse(buildModel)
      val newStringModel = findProperty("prop1")
      verifyPropertyModel(newStringModel, STRING_TYPE, stringValue, STRING, type, 0)
      assertEquals(oldGradleFile, newStringModel.gradleFile)
    }

    run {
      val propertyModel = findProperty("prop1")
      val intValue = 26
      propertyModel.setValue(intValue)
      verifyPropertyModel(propertyModel, INTEGER_TYPE, intValue, INTEGER, type, 0)
      applyChangesAndReparse(buildModel)
      val newIntModel = findProperty("prop1")
      verifyPropertyModel(newIntModel, INTEGER_TYPE, intValue, INTEGER, type, 0)
    }

    run {
      val propertyModel = findProperty("prop1")
      val boolValue = true
      propertyModel.setValue(boolValue)
      verifyPropertyModel(propertyModel, BOOLEAN_TYPE, boolValue, BOOLEAN, type, 0)
      applyChangesAndReparse(buildModel)
      val newBooleanModel = findProperty("prop1")
      verifyPropertyModel(newBooleanModel, BOOLEAN_TYPE, boolValue, BOOLEAN, type, 0)
    }

    run {
      val propertyModel = findProperty("prop1")
      val refValue = "\"${'$'}{prop2}\""
      propertyModel.setValue(refValue)
      // Resolved value and dependencies are only updated after the model has been applied and re-parsed.
      verifyPropertyModel(propertyModel, STRING_TYPE, "ref", STRING, type, 1)
      assertEquals("${'$'}{prop2}", propertyModel.getRawValue(STRING_TYPE))
      applyChangesAndReparse(buildModel)
      val newRefModel = findProperty("prop1")
      verifyPropertyModel(newRefModel, STRING_TYPE, "ref", STRING, type, 1)
      assertEquals("${'$'}{prop2}", newRefModel.getRawValue(STRING_TYPE))
    }
  }

  @Test
  fun testObtainExpressionPsiElement() {
    writeToBuildFile(TestFile.OBTAIN_EXPRESSION_PSI_ELEMENT)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    (if (isGroovy) "'value'" else "\"value\"").let {
      assertThat(extModel.findProperty("prop1").expressionPsiElement!!.text, equalTo(it))
      assertThat(extModel.findProperty("prop1").fullExpressionPsiElement!!.text, equalTo(it))
    }
    assertThat(extModel.findProperty("prop2").expressionPsiElement!!.text, equalTo("25"))
    assertThat(extModel.findProperty("prop2").fullExpressionPsiElement!!.text, equalTo("25"))
    assertThat(extModel.findProperty("prop3").expressionPsiElement!!.text, equalTo("true"))
    assertThat(extModel.findProperty("prop3").fullExpressionPsiElement!!.text, equalTo("true"))
    (if (isGroovy) "[\"key\": 'val']" else "mapOf(\"key\" to \"val\")").let {
      assertThat(extModel.findProperty("prop4").expressionPsiElement!!.text, equalTo(it))
      assertThat(extModel.findProperty("prop4").fullExpressionPsiElement!!.text, equalTo(it))
    }
    (if (isGroovy) "['val1', 'val2', \"val3\"]" else "listOf(\"val1\", \"val2\", \"val3\")").let {
      assertThat(extModel.findProperty("prop5").expressionPsiElement!!.text, equalTo(it))
      assertThat(extModel.findProperty("prop5").fullExpressionPsiElement!!.text, equalTo(it))
    }
    assertThat(extModel.findProperty("prop6").expressionPsiElement!!.text, equalTo("25.3"))
    assertThat(extModel.findProperty("prop6").fullExpressionPsiElement!!.text, equalTo("25.3"))

    val mapItem = extModel.findProperty("prop4").getMapValue("key")
    val listItem = extModel.findProperty("prop5").getListValue("val2")!!
    (if (isGroovy) "'val'" else "\"val\"").let {
      assertThat(mapItem.expressionPsiElement!!.text, equalTo(it))
      assertThat(mapItem.fullExpressionPsiElement!!.text, equalTo(it))
    }
    (if (isGroovy) "'val2'" else "\"val2\"").let {
      assertThat(listItem.expressionPsiElement!!.text, equalTo(it))
      assertThat(listItem.fullExpressionPsiElement!!.text, equalTo(it))
    }

    val configModel = buildModel.android().signingConfigs()[0]!!
    (if (isGroovy) listOf("'my_file.txt'", "file('my_file.txt')") else listOf("\"my_file.txt\"", "file(\"my_file.txt\")")).let {
      assertThat(configModel.storeFile().expressionPsiElement!!.text, equalTo(it[0]))
      assertThat(configModel.storeFile().fullExpressionPsiElement!!.text, equalTo(it[1]))
    }
    assertThat(configModel.storePassword().expressionPsiElement!!.text, equalTo("\"KSTOREPWD\""))
    assertThat(configModel.storePassword().fullExpressionPsiElement!!.text, equalTo("System.getenv(\"KSTOREPWD\")"))
  }

  @Test
  fun testAddToVariable() {
    writeToBuildFile(TestFile.ADD_TO_VARIABLE)

    val buildModel = gradleBuildModel
    val propertyModel = buildModel.ext().findProperty("value")

    verifyPropertyModel(propertyModel.resolve(), INTEGER_TYPE, 1, INTEGER, REGULAR, 1)
  }

  private fun checkContainsValue(models: Collection<GradlePropertyModel>, model: GradlePropertyModel) {
    val result = models.any { areModelsEqual(it, model) }
    assertTrue("checkContainsValue", result)
  }

  @Test
  fun testAddVariableCycle() {
    writeToBuildFile("")

    val buildModel = gradleBuildModel
    val newProperty = buildModel.ext().findProperty("var1")
    newProperty.setValue(ReferenceTo("var1"))

    verifyPropertyModel(newProperty, STRING_TYPE, "var1", REFERENCE, REGULAR, 0)
  }

  /**
   * Tests to ensure that references return the ReferenceTo type when getRawValue is called with either
   * OBJECT_TYPE or REFERENCE_TO_TYPE.
   */
  @Test
  fun testReferenceToReturnObject() {
    writeToBuildFile(TestFile.REFERENCE_TO_RETURN_OBJECT)

    val buildModel = gradleBuildModel
    val property = buildModel.ext().findProperty("goodbye")
    val value = property.getRawValue(OBJECT_TYPE)
    val refValue = property.getRawValue(REFERENCE_TO_TYPE)
    assertThat(value, instanceOf(ReferenceTo::class.java))
    assertThat(refValue, instanceOf(ReferenceTo::class.java))
    val referenceTo = value as ReferenceTo
    val refReferenceTo = refValue as ReferenceTo
    assertThat(referenceTo.text, equalTo(extraName("hello")))
    assertThat(refReferenceTo.text, equalTo(extraName("hello")))
  }

  @Test
  fun testSetBigDecimal() {
    writeToBuildFile(TestFile.SET_REFERENCE_VALUE)

    val buildModel = gradleBuildModel
    buildModel.ext().findProperty("prop1").setValue(BigDecimal(2.343))
    buildModel.ext().findProperty("prop3").setValue(BigDecimal(4.2))
    buildModel.ext().findProperty("newProp").setValue(BigDecimal(3))

    applyChangesAndReparse(buildModel)

    val firstProperty = buildModel.ext().findProperty("prop1")
    verifyPropertyModel(firstProperty, BIG_DECIMAL_TYPE, BigDecimal(2.343), BIG_DECIMAL, REGULAR, 0)
    val secondProperty = buildModel.ext().findProperty("prop3")
    verifyPropertyModel(secondProperty, BIG_DECIMAL_TYPE, BigDecimal(4.2), BIG_DECIMAL, REGULAR, 0)
    val thirdProperty = buildModel.ext().findProperty("newProp")
    verifyPropertyModel(thirdProperty, INTEGER_TYPE, 3, INTEGER, REGULAR, 0)
  }

  @Test
  fun testDuplicateMapKey() {
    writeToBuildFile(TestFile.DUPLICATE_MAP_KEY)

    val buildModel = gradleBuildModel
    val map = buildModel.ext().findProperty("versions").toMap()!!
    assertSize(1, map.keys)
    assertThat(map["firebasePlugins"]!!.forceString(), equalTo("2.1.5"))
  }

  @Test
  fun testParseMapWithSpacesInKeys() {
    writeToBuildFile(TestFile.PARSE_MAP_WITH_SPACES_IN_KEYS)

    val buildModel = gradleBuildModel
    val map = buildModel.ext().findProperty("prop1").toMap()!!
    assertSize(2, map.keys)
    assertThat(map["key1"]!!.forceString(), equalTo("25"))
    assertThat(map["key1 "]!!.forceString(), equalTo("30"))
  }

  private fun verifyDeleteAndResetProperty(buildModel : GradleBuildModel) {
    // Delete and reset the property
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      propertyModel.delete()
      propertyModel.setValue("New Value")
    }

    // Check prop2 hasn't been affected
    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Other Value", STRING, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "New Value", STRING, REGULAR, 0)
    }

    // Check prop2 is still correct
    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Other Value", STRING, REGULAR, 0)
    }
  }

  private fun verifyDeletePropertyInList(buildModel: GradleBuildModel) {
    val extModel = buildModel.ext()

    // Values that should be obtained from the build file.
    val one = 1
    val two = 2

    run {
      val propertyModel = extModel.findProperty("prop1")
      verifyListProperty(propertyModel, listOf<Any>(1, 2, 3, 4), REGULAR, 0)
    }

    run {
      val propertyModel = extModel.findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, one.toString(), STRING, REGULAR, 1, "prop2", "ext.prop2")
      // Check the dependency
      val dependencyModel = propertyModel.dependencies[0]
      verifyPropertyModel(dependencyModel, INTEGER_TYPE, one, INTEGER, DERIVED, 0 /*, "0", "ext.prop1.0" TODO: FIX THIS */)
      // Delete this property.
      dependencyModel.delete()
    }

    applyChangesAndReparse(buildModel)

    // Check that the value of prop2 has changed
    run {
      val propertyModel = gradleBuildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, two.toString(), STRING, REGULAR, 1, "prop2", "ext.prop2")
      val dependencyModel = propertyModel.dependencies[0]
      verifyPropertyModel(dependencyModel, INTEGER_TYPE, two, INTEGER, DERIVED, 0 /*, "0", "ext.prop1.0" TODO: FIX THIS */)
    }
  }

  private fun verifySetNewValueInMap(buildModel: GradleBuildModel) {
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)

      run {
        val map = propertyModel.getValue(MAP_TYPE)!!
        assertSize(2, map.entries)
        verifyPropertyModel(map["key1"], STRING_TYPE, "value1", STRING, DERIVED, 0)
        verifyPropertyModel(map["key2"], STRING_TYPE, "value2", STRING, DERIVED, 0)

        // Attempt to set a new value.
        val newValue = propertyModel.getMapValue("key3")
        verifyPropertyModel(newValue, OBJECT_TYPE, null, NONE, DERIVED, 0)
        newValue.setValue(true)
        verifyPropertyModel(newValue, BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)
      }

      run {
        // Check map now has three values.
        val map = propertyModel.getValue(MAP_TYPE)!!
        assertSize(3, map.entries)
        verifyPropertyModel(map["key3"], BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)
        verifyPropertyModel(map["key1"], STRING_TYPE, "value1", STRING, DERIVED, 0)
        verifyPropertyModel(map["key2"], STRING_TYPE, "value2", STRING, DERIVED, 0)
      }
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(3, map.entries)
      verifyPropertyModel(map["key3"], BOOLEAN_TYPE, true, BOOLEAN, DERIVED, 0)
      verifyPropertyModel(map["key1"], STRING_TYPE, "value1", STRING, DERIVED, 0)
      verifyPropertyModel(map["key2"], STRING_TYPE, "value2", STRING, DERIVED, 0)
    }
  }

  private fun verifyDeletePropertyInMap(buildModel: GradleBuildModel, testFileName: TestFileName) {
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      run {
        val map = propertyModel.getValue(MAP_TYPE)!!
        assertSize(2, map.entries)
        verifyPropertyModel(map["key1"], STRING_TYPE, "value1", STRING, DERIVED, 0)
        verifyPropertyModel(map["key2"], STRING_TYPE, "value2", STRING, DERIVED, 0)
        map["key1"]?.delete()
      }

      run {
        val map = propertyModel.getValue(MAP_TYPE)!!
        assertSize(1, map.entries)
        verifyPropertyModel(map["key2"], STRING_TYPE, "value2", STRING, DERIVED, 0)
      }
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)
      verifyPropertyModel(map["key2"], STRING_TYPE, "value2", STRING, DERIVED, 0)
    }

    verifyFileContents(myBuildFile, testFileName)
  }

  private fun verifySetNewValueInEmptyMap(buildModel: GradleBuildModel) {
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)

      // Check every thing is in order
      run {
        val map = propertyModel.getValue(MAP_TYPE)!!
        assertSize(0, map.entries)
      }

      // Set the new value.
      propertyModel.getMapValue("key1").setValue(ReferenceTo("val1"))

      // Check the correct values are shown in the property.
      run {
        val map = propertyModel.getValue(MAP_TYPE)!!
        assertSize(1, map.entries)
        verifyPropertyModel(map["key1"], STRING_TYPE, "val1", REFERENCE, DERIVED, 1)
        verifyPropertyModel(map["key1"]!!.dependencies[0], STRING_TYPE, "value", STRING, VARIABLE, 0)
      }
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)
      verifyPropertyModel(map["key1"], STRING_TYPE, "val1", REFERENCE, DERIVED, 1)
      verifyPropertyModel(map["key1"]!!.dependencies[0], STRING_TYPE, "value", STRING, VARIABLE, 0)
    }
  }

  private fun verifyDeleteMapItemToAndSetFromEmpty(buildModel: GradleBuildModel) {
    // Delete the item in the map.
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)
      verifyPropertyModel(map["key1"], INTEGER_TYPE, 25, INTEGER, DERIVED, 0)

      map["key1"]?.delete()

      val newMap = propertyModel.getValue(MAP_TYPE)!!
      assertSize(0, newMap.entries)
    }

    applyChangesAndReparse(buildModel)

    // Check that a reparse still has a missing model
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      assertSize(0, propertyModel.getValue(MAP_TYPE)!!.entries)

      // Attempt to set a new value
      propertyModel.getMapValue("Conquest").setValue("Famine")
      // Check the model again
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      verifyPropertyModel(map["Conquest"], STRING_TYPE, "Famine", STRING, DERIVED, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      verifyPropertyModel(map["Conquest"], STRING_TYPE, "Famine", STRING, DERIVED, 0)
    }
  }

  private fun verifySetMapValueToLiteralForKTSArrayExpression(buildModel: GradleBuildModel) {
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)

      // Set the property to a new value.
      propertyModel.setValue(77)
      verifyPropertyModel(propertyModel, INTEGER_TYPE, 77, INTEGER, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, INTEGER_TYPE, 77, INTEGER, REGULAR, 0)
    }
  }

  private fun verifyDeleteToEmptyMap(buildModel: GradleBuildModel) {
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      assertSize(3, propertyModel.getValue(MAP_TYPE)!!.entries)

      val map = propertyModel.getValue(MAP_TYPE)!!
      map["key"]!!.delete()
      map["key1"]!!.delete()
      map["key2"]!!.delete()

      assertEquals(MAP, propertyModel.valueType)
      assertSize(0, propertyModel.getValue(MAP_TYPE)!!.entries)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      assertSize(0, propertyModel.getValue(MAP_TYPE)!!.entries)
    }
  }

  private fun verifyAddExistingMapProperty(buildModel: GradleBuildModel) {
    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyMapProperty(propertyModel, ImmutableMap.of("key", "val") as Map<String, Any>)

      propertyModel.getMapValue("key").setValue("newVal")
      verifyMapProperty(propertyModel, ImmutableMap.of("key", "newVal") as Map<String, Any>)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyMapProperty(propertyModel, ImmutableMap.of("key", "newVal") as Map<String, Any>)
    }
  }

  private fun verifyDeleteMapProperty(buildModel: GradleBuildModel) {
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      assertSize(1, propertyModel.getValue(MAP_TYPE)!!.entries)

      // Delete the map
      propertyModel.delete()
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)
    }
  }

  private fun verifyDeleteEmptyMap(buildModel: GradleBuildModel) {
    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      assertEquals(MAP, propertyModel.valueType)
      assertSize(0, propertyModel.getValue(MAP_TYPE)!!.entries)

      propertyModel.delete()
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)
    }
  }

  private fun verifySetLiteralToMapValue(buildModel: GradleBuildModel) {
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "val1", REFERENCE, REGULAR, 1)
      val deps = propertyModel.dependencies
      assertSize(1, deps)
      val mapPropertyModel = deps[0]
      // Check it is not a map yet
      verifyPropertyModel(mapPropertyModel, STRING_TYPE, "value", STRING, VARIABLE, 0)

      mapPropertyModel.convertToEmptyMap().getMapValue("key").setValue("Hello")

      assertEquals(MAP, mapPropertyModel.valueType)
      val map = mapPropertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)
      verifyPropertyModel(map["key"], STRING_TYPE, "Hello", STRING, DERIVED, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "val1", REFERENCE, REGULAR, 1)
      val deps = propertyModel.dependencies
      assertSize(1, deps)
      val mapPropertyModel = deps[0]
      assertEquals(MAP, mapPropertyModel.valueType)
      val map = mapPropertyModel.getValue(MAP_TYPE)!!
      assertSize(1, map.entries)
      verifyPropertyModel(map["key"], STRING_TYPE, "Hello", STRING, DERIVED, 0)
    }
  }

  private fun verifyParseMapInMap(buildModel: GradleBuildModel) {
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(2, map.entries)
      verifyPropertyModel(map["key1"], INTEGER_TYPE, 25, INTEGER, DERIVED, 0)

      val mapPropertyModel = map["key2"]!!
      assertEquals(MAP, mapPropertyModel.valueType)
      val innerMap = mapPropertyModel.getValue(MAP_TYPE)!!
      assertSize(1, innerMap.entries)
      verifyPropertyModel(innerMap["key"], STRING_TYPE, "value", STRING, DERIVED, 0)
    }
  }

  private fun verifyMapsInMap(buildModel: GradleBuildModel, fileName: TestFileName) {
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(3, map.entries)

      val firstInnerMapModel = map["key1"]!!
      assertEquals(MAP, firstInnerMapModel.valueType)
      val firstInnerMap = firstInnerMapModel.getValue(MAP_TYPE)!!
      assertSize(1, firstInnerMap.entries)
      // Delete the first inner map
      firstInnerMapModel.delete()

      // Check is has been deleted.
      verifyPropertyModel(firstInnerMapModel, OBJECT_TYPE, null, NONE, DERIVED, 0)

      val secondInnerMapModel = map["key3"]!!
      assertEquals(MAP, secondInnerMapModel.valueType)
      val secondInnerMap = secondInnerMapModel.getValue(MAP_TYPE)!!
      assertSize(2, secondInnerMap.entries)
      verifyPropertyModel(secondInnerMap["key4"], STRING_TYPE, "value2", STRING, DERIVED, 0)
      verifyPropertyModel(secondInnerMap["key5"], INTEGER_TYPE, 43, INTEGER, DERIVED, 0)
      // Delete one of these values, and change the other.
      secondInnerMap["key4"]!!.setValue(ReferenceTo("var1"))
      secondInnerMap["key5"]!!.delete()

      // Check the values are correct.
      verifyPropertyModel(secondInnerMap["key4"], STRING_TYPE, "var1", REFERENCE, DERIVED, 1)
      verifyPropertyModel(secondInnerMap["key5"], OBJECT_TYPE, null, NONE, DERIVED, 0)

      val thirdInnerMapModel = map["key6"]!!
      assertEquals(MAP, thirdInnerMapModel.valueType)
      val thirdInnerMap = thirdInnerMapModel.getValue(MAP_TYPE)!!
      assertSize(1, thirdInnerMap.entries)
      verifyPropertyModel(thirdInnerMap["key7"], STRING_TYPE, "value3", STRING, DERIVED, 0)

      // Set this third map model to be another basic value.
      thirdInnerMapModel.setValue(77)

      // Check it has been deleted.
      verifyPropertyModel(thirdInnerMapModel, INTEGER_TYPE, 77, INTEGER, DERIVED, 0)
    }

    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, fileName)

    // Check everything is in order after a reparse.
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      val map = propertyModel.getValue(MAP_TYPE)!!
      assertSize(2, map.entries)

      val firstInnerMapModel = map["key1"]
      assertNull(firstInnerMapModel)

      val secondInnerMapModel = map["key3"]!!
      assertEquals(MAP, secondInnerMapModel.valueType)
      val secondInnerMap = secondInnerMapModel.getValue(MAP_TYPE)!!
      assertSize(1, secondInnerMap.entries)
      verifyPropertyModel(secondInnerMap["key4"], STRING_TYPE, "var1", REFERENCE, DERIVED, 1)
      assertNull(secondInnerMap["key5"])

      val thirdInnerMapModel = map["key6"]!!
      verifyPropertyModel(thirdInnerMapModel, INTEGER_TYPE, 77, INTEGER, DERIVED, 0)
    }
  }

  fun assertSize(expectedSize: Int, list: MutableList<*>?) {
    UsefulTestCase.assertSize(expectedSize, list!!)  // second param is @NotNull as of commit 8bd1b49
  }

  private fun arrayExpressionSyntaxTestIsIrrelevantForGroovy() {
    isIrrelevantForGroovy("tests KotlinScript-specific array expression syntax")
  }

  enum class TestFile(val path: @SystemDependent String): TestFileName {
    PROPERTIES("properties"),
    PROPERTIES_FROM_SCRATCH("propertiesFromScratch"),
    PROPERTIES_FROM_SCRATCH_EXPECTED("propertiesFromScratchExpected"),
    PROPERTIES_FROM_SCRATCH_ARRAY_EXPRESSION("propertiesFromScratchArrayExpression"),
    PROPERTIES_FROM_SCRATCH_ARRAY_EXPRESSION_EXPECTED("propertiesFromScratchArrayExpressionExpected"),
    VARIABLES("variables"),
    UNKNOWN_VALUES("unknownValues"),
    UNKNOWN_VALUES_IN_MAP("unknownValuesInMap"),
    UNKNOWN_VALUES_IN_LIST("unknownValuesInList"),
    GET_PROPERTIES("getProperties"),
    GET_VARIABLES("getVariables"),
    AS_TYPE("asType"),
    GET_NON_QUOTED_LIST_INDEX("getNonQuotedListIndex"),
    REFERENCE_PROPERTY_DEPENDENCY("referencePropertyDependency"),
    INTEGER_REFERENCE_PROPERTY_DEPENDENCY("integerReferencePropertyDependency"),
    REFERENCE_VARIABLE_DEPENDENCY("referenceVariableDependency"),
    CREATE_AND_DELETE_LIST_TO_EMPTY("createAndDeleteListToEmpty"),
    CREATE_AND_DELETE_LIST_TO_EMPTY_EXPECTED("createAndDeleteListToEmptyExpected"),
    CREATE_AND_DELETE_PLACE_HOLDERS_TO_EMPTY("createAndDeletePlaceHoldersToEmpty"),
    CREATE_AND_DELETE_PLACE_HOLDERS_TO_EMPTY_EXPECTED("createAndDeletePlaceHoldersToEmptyExpected"),
    CREATE_AND_DELETE_MAP_TO_EMPTY("createAndDeleteMapToEmpty"),
    CREATE_AND_DELETE_MAP_TO_EMPTY_EXPECTED("createAndDeleteMapToEmptyExpected"),
    REFERENCE_MAP_DEPENDENCY("referenceMapDependency"),
    REFERENCE_LIST_DEPENDENCY("referenceListDependency"),
    PROPERTY_DEPENDENCY("propertyDependency"),
    VARIABLE_DEPENDENCY("variableDependency"),
    PROPERTY_VARIABLE_DEPENDENCY("propertyVariableDependency"),
    VARIABLE_PROPERTY_DEPENDENCY("variablePropertyDependency"),
    MULTIPLE_DEPENDENCIES_WITH_FULLY_QUALIFIED_NAME("multipleDependenciesWithFullyQualifiedName"),
    MULTIPLE_TYPE_DEPENDENCIES_WITH_FULLY_QUALIFIED_NAME("multipleTypeDependenciesWithFullyQualifiedName"),
    NESTED_LIST_PROPERTY_INJECTION("nestedListPropertyInjection"),
    NESTED_MAP_VARIABLE_INJECTION("nestedMapVariableInjection"),
    LIST_DEPENDENCY("listDependency"),
    MAP_DEPENDENCY("mapDependency"),
    OUT_OF_SCOPE_MAP_AND_LIST_DEPENDENCIES("outOfScopeMapAndListDependencies"),
    DEEP_DEPENDENCIES("deepDependencies"),
    DEPENDENCIES_IN_MAP("dependenciesInMap"),
    GET_FILE("getFile"),
    PROPERTY_SET_VALUE("propertySetValue"),
    VARIABLE_SET_VALUE("variableSetValue"),
    SET_UNKNOWN_VALUE_TYPE("setUnknownValueType"),
    ESCAPE_SET_STRINGS("escapeSetStrings"),
    QUOTES_IN_STRING("quotesInString"),
    SET_BOTH_STRING_TYPES("setBothStringTypes"),
    SET_GARBAGE_REFERENCE("setGarbageReference"),
    SET_REFERENCE_WITH_MODEL("setReferenceWithModel"),
    SET_REFERENCE_WITH_MODEL_EXPECTED("setReferenceWithModelExpected"),
    QUOTES_WITHIN_QUOTES("quotesWithinQuotes"),
    QUOTES_WITHIN_QUOTES_EXPECTED("quotesWithinQuotesExpected"),
    SET_REFERENCE_VALUE("setReferenceValue"),
    SET_REFERENCE_VALUE_EXPECTED("setReferenceValueExpected"),
    CHANGE_PROPERTY_TYPE_TO_REFERENCE("changePropertyTypeToReference"),
    CHANGE_PROPERTY_TYPE_TO_REFERENCE_EXPECTED("changePropertyTypeToReferenceExpected"),
    CHANGE_PROPERTY_TYPE_TO_LITERAL("changePropertyTypeToLiteral"),
    CHANGE_PROPERTY_TYPE_TO_LITERAL_EXPECTED("changePropertyTypeToLiteralExpected"),
    DEPENDENCY_CHANGED_UPDATES_VALUE("dependencyChangedUpdatesValue"),
    DEPENDENCY_CHANGED_UPDATES_VALUE_EXPECTED("dependencyChangedUpdatesValueExpected"),
    DEPENDENCY_BASIC_CYCLE("dependencyBasicCycle"),
    DEPENDENCY_BASIC_CYCLE_REFERENCE("dependencyBasicCycleReference"),
    DEPENDENCY_NO_CYCLE4_DEPTH("dependencyNoCycle4Depth"),
    DEPENDENCY_TWICE("dependencyTwice"),
    DEPENDENCY_NO_CYCLE("dependencyNoCycle"),
    DELETE_PROPERTY("deleteProperty"),
    DELETE_VARIABLE("deleteVariable"),
    DELETE_VARIABLE_EXPECTED("deleteVariableExpected"),
    DELETE_AND_RESET_PROPERTY("deleteAndResetProperty"),
    DELETE_AND_RESET_KTS_ARRAY_EXPRESSION_PROPERTY("deleteAndResetKTSArrayExpressionProperty"),
    UPDATE_PROPERTY_WITHOUT_SYNTAX_CHANGE("updatePropertyValueWithoutSyntaxChange"),
    UPDATE_PROPERTY_WITHOUT_SYNTAX_CHANGE_EXPECTED("updatePropertyValueWithoutSyntaxChangeExpected"),
    DELETE_VARIABLE_DEPENDENCY("deleteVariableDependency"),
    DELETE_VARIABLE_DEPENDENCY_EXPECTED("deleteVariableDependencyExpected"),
    CHECK_SETTING_DELETED_MODEL("checkSettingDeletedModel"),
    CHECK_SETTING_DELETED_MODEL_EXPECTED("checkSettingDeletedModelExpected"),
    DELETE_PROPERTY_IN_LIST("deletePropertyInList"),
    DELETE_ARRAY_EXPRESSION_PROPERTY_IN_LIST("deleteArrayExpressionPropertyInList"),
    CREATE_NEW_EMPTY_MAP_VALUE_EXPECTED("createNewEmptyMapValueExpected"),
    ADD_MAP_VALUE_TO_STRING("addMapValueToString"),
    SET_NEW_VALUE_IN_MAP("setNewValueInMap"),
    SET_NEW_VALUE_IN_MAP_FOR_ARRAY_EXPRESSION("setNewValueInMapForArrayExpression"),
    SET_NEW_VALUE_IN_EMPTY_MAP("setNewValueInEmptyMap"),
    SET_NEW_VALUE_IN_EMPTY_MAP_FOR_KTS_ARRAY_EXPRESSION("setNewValueInEmptyMapForKTSArrayExpression"),
    DELETE_PROPERTY_IN_MAP("deletePropertyInMap"),
    DELETE_PROPERTY_IN_MAP_FOR_KTS_ARRAY_EXPRESSION("deletePropertyInMapForKTSArrayExpression"),
    DELETE_PROPERTY_IN_MAP_EXPECTED("deletePropertyInMapExpected"),
    DELETE_PROPERTY_IN_MAP_FOR_KTS_ARRAY_EXPRESSION_EXPECTED("deletePropertyInMapForKTSArrayExpressionExpected"),
    DELETE_MAP_ITEM_TO_AND_SET_FROM_EMPTY("deleteMapItemToAndSetFromEmpty"),
    DELETE_MAP_ITEM_TO_AND_SET_FROM_EMPTY_FOR_KTS_ARRAY_EXPRESSION("deleteMapItemToAndSetFromEmptyForKTSArrayExpression"),
    SET_MAP_VALUE_TO_LITERAL("setMapValueToLiteral"),
    SET_MAP_VALUE_TO_LITERAL_FOR_KTS_ARRAY_EXPRESSION("setMapValueToLiteralForKTSArrayExpression"),
    DELETE_TO_EMPTY_MAP("deleteToEmptyMap"),
    DELETE_TO_EMPTY_MAP_FOR_KTS_ARRAY_EXPRESSION("deleteToEmptyMapForKTSArrayExpression"),
    ADD_EXISTING_MAP_PROPERTY("addExistingMapProperty"),
    ADD_EXISTING_MAP_PROPERTY_FOR_KTS_ARRAY_EXPRESSION("addExistingMapPropertyForKTSArrayExpression"),
    DELETE_MAP_PROPERTY("deleteMapProperty"),
    DELETE_MAP_PROPERTY_FOR_KTS_ARRAY_EXPRESSION("deleteMapPropertyForKTSArrayExpression"),
    DELETE_MAP_VARIABLE("deleteMapVariable"),
    DELETE_MAP_VARIABLE_EXPECTED("deleteMapVariableExpected"),
    DELETE_EMPTY_MAP("deleteEmptyMap"),
    DELETE_EMPTY_MAP_FOR_KTS_ARRAY_EXPRESSION("deleteEmptyMapForKTSArrayExpression"),
    SET_LITERAL_TO_MAP_VALUE("setLiteralToMapValue"),
    SET_LITERAL_TO_MAP_VALUE_FOR_KTS_ARRAY_EXPRESSION("setLiteralToMapValueForKTSArrayExpression"),
    PARSE_MAP_IN_MAP("parseMapInMap"),
    PARSE_MAP_IN_MAP_FOR_KTS_ARRAY_EXPRESSION("parseMapInMapForKTSArrayExpression"),
    MAPS_IN_MAP("mapsInMap"),
    MAPS_IN_MAP_FOR_KTS_ARRAY_EXPRESSION("mapsInMapForKTSArrayExpression"),
    MAPS_IN_MAP_EXPECTED("mapsInMapExpected"),
    MAPS_IN_MAP_FOR_KTS_ARRAY_EXPRESSION_EXPECTED("mapsInMapForKTSArrayExpressionExpected"),
    MAP_ORDER("mapOrder"),
    SET_MAP_IN_MAP("setMapInMap"),
    SET_MAP_IN_MAP_EXPECTED("setMapInMapExpected"),
    CREATE_NEW_EMPTY_LIST_EXPECTED("createNewEmptyListExpected"),
    CONVERT_TO_EMPTY_LIST("convertToEmptyList"),
    CONVERT_TO_EMPTY_LIST_EXPECTED("convertToEmptyListExpected"),
    ADD_TO_NONE_LIST("addToNoneList"),
    ADD_OUT_OF_BOUNDS("addOutOfBounds"),
    SET_LIST_IN_MAP("setListInMap"),
    SET_LIST_IN_MAP_EXPECTED("setListInMapExpected"),
    SET_TO_LIST_VALUES("setToListValues"),
    ADD_SINGLE_ELEMENT_TO_EMPTY("addSingleElementToEmpty"),
    ADD_SINGLE_ELEMENT_TO_EMPTY_EXPECTED("addSingleElementToEmptyExpected"),
    ADD_TO_AND_DELETE_LIST_FROM_EMPTY("addToAndDeleteListFromEmpty"),
    ADD_TO_AND_DELETE_LIST_FROM_EMPTY_EXPECTED("addToAndDeleteListFromEmptyExpected"),
    ADD_AND_REMOVE_FROM_NON_LITERAL_LIST("addAndRemoveFromNonLiteralList"),
    ADD_AND_REMOVE_FROM_NON_LITERAL_LIST_EXPECTED("addAndRemoveFromNonLiteralListExpected"),
    SET_LIST("setList"),
    SET_LIST_EXPECTED("setListExpected"),
    ADD_MIDDLE_OF_LIST("addMiddleOfList"),
    ADD_MIDDLE_OF_LIST_EXPECTED("addMiddleOfListExpected"),
    SET_IN_MIDDLE_OF_LIST("setInMiddleOfList"),
    SET_IN_MIDDLE_OF_LIST_EXPECTED("setInMiddleOfListExpected"),
    SET_VALUE_IN_MAP("setValueInMap"),
    SET_VALUE_IN_MAP_EXPECTED("setValueInMapExpected"),
    SET_MAP_VALUE_ON_NONE_MAP("setMapValueOnNoneMap"),
    OUTER_SCOPE_VARIABLES_RESOLVED("outerScopeVariablesResolved"),
    OUTER_SCOPE_VARIABLES_RESOLVED_EXPECTED("outerScopeVariablesResolvedExpected"),
    VARIABLES_FROM_NESTED_APPLY("variablesFromNestedApply"),
    VARIABLES_FROM_NESTED_APPLY_EXPECTED("variablesFromNestedApplyExpected"),
    VARIABLES_FROM_NESTED_APPLY_APPLIED_FILE_ONE("variablesFromNestedApplyAppliedFileOne"),
    VARIABLES_FROM_NESTED_APPLY_APPLIED_FILE_ONE_EXPECTED("variablesFromNestedApplyAppliedFileOneExpected"),
    VARIABLES_FROM_NESTED_APPLY_APPLIED_FILE_TWO("variablesFromNestedApplyAppliedFileTwo"),
    APPLICATION_CYCLE("applicationCycle"),
    APPLICATION_CYCLE_APPLIED("applicationCycleApplied"),
    VARIABLES_FROM_APPLY("variablesFromApply"),
    VARIABLES_FROM_APPLY_APPLIED("variablesFromApplyApplied"),
    VARIABLES_FROM_APPLY_APPLIED_EXPECTED("variablesFromApplyAppliedExpected"),
    ADD_REMOVE_REFERENCE_VALUES("addRemoveReferenceValues"),
    ADD_REMOVE_REFERENCE_VALUES_EXPECTED("addRemoveReferenceValuesExpected"),
    RENAME("rename"),
    RENAME_EXPECTED("renameExpected"),
    RENAME_MAP_PROPERTY_AND_KEYS("renameMapPropertyAndKeys"),
    RENAME_MAP_PROPERTY_AND_KEYS_EXPECTED("renameMapPropertyAndKeysExpected"),
    RENAME_LIST_VALUE_THROWS("renameListValueThrows"),
    RENAME_LIST_VALUE_THROWS_EXPECTED("renameListValueThrowsExpected"),
    GET_DECLARED_PROPERTIES("getDeclaredProperties"),
    DELETE_ITEMS_FROM_LIST("deleteItemsFromList"),
    DELETE_ITEMS_FROM_LIST_EXPECTED("deleteItemsFromListExpected"),
    DELETE_LIST_WITH_ITEMS("deleteListWithItems"),
    DELETE_ITEMS_IN_MAP("deleteItemsInMap"),
    DELETE_ITEMS_IN_MAP_EXPECTED("deleteItemsInMapExpected"),
    DELETE_MAP_WITH_ITEMS("deleteMapWithItems"),
    OBTAIN_EXPRESSION_PSI_ELEMENT("obtainExpressionPsiElement"),
    ADD_TO_VARIABLE("addToVariable"),
    REFERENCE_TO_RETURN_OBJECT("referenceToReturnObject"),
    RESOLVE_AND_SET_VARIABLES_IN_PARENT_MODULE("resolveAndSetVariablesInParentModule"),
    RESOLVE_AND_SET_VARIABLES_IN_PARENT_MODULE_EXPECTED("resolveAndSetVariablesInParentModuleExpected"),
    RESOLVE_VARIABLES_IN_PARENT_MODULE_SUB("resolveAndSetVariablesInParentModule_sub"),
    RESOLVE_VARIABLES_IN_PROPERTIES_FILE("resolveVariablesInPropertiesFile"),
    RESOLVE_VARIABLES_IN_PROPERTIES_FILE_SUB("resolveVariablesInPropertiesFile_sub"),
    IN_SCOPE_ELEMENT("inScopeElement"),
    IN_SCOPE_ELEMENT_SUB("inScopeElement_sub"),
    DUPLICATE_MAP_KEY("duplicateMapKey"),
    PARSE_MAP_WITH_SPACES_IN_KEYS("parseMapWithSpacesInKeys"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/gradlePropertyModel/$path", extension)
    }
  }
}
