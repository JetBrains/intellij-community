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
import com.android.tools.idea.gradle.dsl.TestFileName.*
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.*
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.*
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl
import com.google.common.collect.ImmutableMap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.math.BigDecimal

class GradlePropertyModelTest : GradleFileModelTestCase() {
  @Test
  fun testPropertyFromScratch() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_PROPERTIES_FROM_SCRATCH)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    extModel.findProperty("newProp").setValue(123)
    extModel.findProperty("prop1").setValue(ReferenceTo("newProp"))

    applyChangesAndReparse(buildModel)

    // We don't have to cast extra properties when declaring extra properties so the cast is not necessary.
    //TODO(karimai): ugly, we shouldn't cast when the applyContext is an extra property.
    verifyFileContents(myBuildFile, GRADLE_PROPERTY_MODEL_PROPERTIES_FROM_SCRATCH_EXPECTED)
  }

  @Test
  fun testPropertyFromScratchArrayExpression() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_PROPERTIES_FROM_SCRATCH_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    extModel.findProperty("ext.newProp").setValue(123)
    extModel.findProperty("ext.prop1").setValue(ReferenceTo("newProp"))
    extModel.findProperty("prop2").setValue(ReferenceTo("ext.newProp"))

    applyChangesAndReparse(buildModel)

    verifyFileContents(myBuildFile, GRADLE_PROPERTY_MODEL_PROPERTIES_FROM_SCRATCH_ARRAY_EXPRESSION_EXPECTED)
  }

  @Test
  fun testProperties() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_PROPERTIES)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_VARIABLES)

    val extModel = gradleBuildModel.ext()

    run {
      val propertyModel = extModel.findProperty("prop1")
      assertEquals(STRING, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals("value", propertyModel.getValue(STRING_TYPE))
      assertEquals("value", propertyModel.getRawValue(STRING_TYPE))
      assertEquals("prop1", propertyModel.name)
      assertEquals("ext.prop1", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop2")
      assertEquals(INTEGER, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals(25, propertyModel.getValue(INTEGER_TYPE))
      assertEquals(25, propertyModel.getRawValue(INTEGER_TYPE))
      assertEquals("prop2", propertyModel.name)
      assertEquals("ext.prop2", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop3")
      assertEquals(BOOLEAN, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals(true, propertyModel.getValue(BOOLEAN_TYPE))
      assertEquals(true, propertyModel.getRawValue(BOOLEAN_TYPE))
      assertEquals("prop3", propertyModel.name)
      assertEquals("ext.prop3", propertyModel.fullyQualifiedName)
    }

    run {
      val propertyModel = extModel.findProperty("prop4")
      assertEquals(MAP, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals("prop4", propertyModel.name)
      assertEquals("ext.prop4", propertyModel.fullyQualifiedName)
      val value = propertyModel.getValue(MAP_TYPE)!!["key"]!!
      assertEquals("val", value.getValue(STRING_TYPE))
      assertEquals(DERIVED, value.propertyType)
      assertEquals(STRING, value.valueType)
    }

    run {
      val propertyModel = extModel.findProperty("prop5")
      assertEquals(LIST, propertyModel.valueType)
      assertEquals(VARIABLE, propertyModel.propertyType)
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
      assertEquals(VARIABLE, propertyModel.propertyType)
      assertEquals("prop6", propertyModel.name)
      assertEquals("ext.prop6", propertyModel.fullyQualifiedName)
    }
  }

  @Test
  fun testUnknownValues() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_UNKNOWN_VALUES)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_UNKNOWN_VALUES_IN_MAP)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_UNKNOWN_VALUES_IN_LIST)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_GET_PROPERTIES)

    val extModel = gradleBuildModel.ext()
    val properties = extModel.properties
    // Note: this shouldn't include variables.
    assertSize(3, properties)

    verifyPropertyModel(properties[0], STRING_TYPE, "var1", REFERENCE, REGULAR, 1, "prop1", "ext.prop1")
    verifyPropertyModel(properties[0].dependencies[0], STRING_TYPE, "Value1", STRING, VARIABLE, 0, "var1", "ext.var1")
    verifyPropertyModel(properties[1], STRING_TYPE, "Cool Value2", STRING, REGULAR, 1, "prop2", "ext.prop2")
    verifyPropertyModel(properties[1].dependencies[0], STRING_TYPE, "Value2", STRING, VARIABLE, 0, "var2", "ext.var2")
    verifyPropertyModel(properties[2], STRING_TYPE, "Nice Value3", STRING, REGULAR, 1, "prop3", "ext.prop3")
    verifyPropertyModel(properties[2].dependencies[0], STRING_TYPE, "Value3", STRING, VARIABLE, 0, "var3", "ext.var3")
  }

  @Test
  fun testGetVariables() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_GET_VARIABLES)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_AS_TYPE)

    val extModel = gradleBuildModel.ext()

    run {
      val stringModel = extModel.findProperty("prop1")
      assertEquals("value", stringModel.valueAsString())
      assertNull(stringModel.toInt())
      assertNull(stringModel.toBoolean())
      assertNull(stringModel.toList())
      assertNull(stringModel.toMap())
      val intModel = extModel.findProperty("prop2")
      assertEquals(25, intModel.toInt())
      assertEquals("25", intModel.valueAsString())
      assertNull(intModel.toBoolean())
      assertNull(intModel.toMap())
      assertNull(intModel.toList())
      val boolModel = extModel.findProperty("prop3")
      assertEquals(true, boolModel.toBoolean())
      assertEquals("true", boolModel.valueAsString())
      assertNull(boolModel.toInt())
      val mapModel = extModel.findProperty("prop4")
      assertNotNull(mapModel.toMap())
      assertNull(mapModel.toInt())
      assertNull(mapModel.toList())
      val listModel = extModel.findProperty("prop5")
      assertNotNull(listModel.toList())
      assertNull(listModel.toBoolean())
      assertNull(listModel.toMap())
    }
  }

  @Test
  fun testGetNonQuotedListIndex() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_GET_NON_QUOTED_LIST_INDEX)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_REFERENCE_PROPERTY_DEPENDENCY)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_INTEGER_REFERENCE_PROPERTY_DEPENDENCY)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_REFERENCE_VARIABLE_DEPENDENCY)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_CREATE_AND_DELETE_LIST_TO_EMPTY)

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

    run {
      val propertyModel = extModel.findProperty("prop2")
      verifyListProperty(propertyModel, listOf(), true)
    }
  }

  @Test
  fun testCreateAndDeletePlaceHoldersToEmpty() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_CREATE_AND_DELETE_PLACE_HOLDERS_TO_EMPTY)

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
    verifyFileContents(myBuildFile, GRADLE_PROPERTY_MODEL_CREATE_AND_DELETE_PLACE_HOLDERS_TO_EMPTY_EXPECTED)

    run {
      val propertyModel = buildModel.android().defaultConfig().manifestPlaceholders()
      verifyEmptyMapProperty(propertyModel)
    }
  }

  @Test
  fun testCreateAndDeleteMapToEmpty() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_CREATE_AND_DELETE_MAP_TO_EMPTY)

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

    run {
      val propertyModel = extModel.findProperty("prop2")
      verifyMapProperty(propertyModel, mapOf())
    }
  }

  @Test
  fun testReferenceMapDependency() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_REFERENCE_MAP_DEPENDENCY)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_REFERENCE_LIST_DEPENDENCY)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_PROPERTY_DEPENDENCY)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_VARIABLE_DEPENDENCY)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_PROPERTY_VARIABLE_DEPENDENCY)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_VARIABLE_PROPERTY_DEPENDENCY)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("\${prop1} world!", propertyModel.getRawValue(STRING_TYPE))
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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_MULTIPLE_DEPENDENCIES_WITH_FULLY_QUALIFIED_NAME)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_MULTIPLE_TYPE_DEPENDENCIES_WITH_FULLY_QUALIFIED_NAME)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("true and value1", propertyModel.getValue(STRING_TYPE))
    assertEquals("\${prop1} and \${project.ext.prop1}", propertyModel.getRawValue(STRING_TYPE))

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
      assertEquals("ext.prop1", value.fullyQualifiedName)
      assertEquals(STRING, value.valueType)
      assertEquals(REGULAR, value.propertyType)
    }
  }

  @Test
  fun testNestedListPropertyInjection() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_NESTED_LIST_PROPERTY_INJECTION)

    val propertyModel = gradleBuildModel.ext().findProperty("prop4")
    assertEquals("3", propertyModel.getValue(STRING_TYPE))
    assertEquals("${'$'}{prop3.key[0][2]}", propertyModel.getRawValue(STRING_TYPE))
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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_NESTED_MAP_VARIABLE_INJECTION)

    val propertyModel = gradleBuildModel.ext().findProperty("prop3")
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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_LIST_DEPENDENCY)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_MAP_DEPENDENCY)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop2")
    assertEquals("value", propertyModel.getValue(STRING_TYPE))
    assertEquals("\${prop1.key}", propertyModel.getRawValue(STRING_TYPE))

    val deps = propertyModel.dependencies
    val value = deps[0]
    assertEquals("value", value.getValue(STRING_TYPE))
    assertEquals(DERIVED, value.propertyType)
    assertEquals(STRING, value.valueType)
  }

  @Test
  fun testOutOfScopeMapAndListDependencies() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_OUT_OF_SCOPE_MAP_AND_LIST_DEPENDENCIES)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DEEP_DEPENDENCIES)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DEPENDENCIES_IN_MAP)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_GET_FILE)

    val extModel = gradleBuildModel.ext()
    val propertyModel = extModel.findProperty("prop1")
    assertEquals(propertyModel.gradleFile, myBuildFile)
  }

  @Test
  fun testPropertySetValue() {
    runSetPropertyTest(GRADLE_PROPERTY_MODEL_PROPERTY_SET_VALUE, REGULAR)
  }

  @Test
  fun testVariableSetValue() {
    runSetPropertyTest(GRADLE_PROPERTY_MODEL_VARIABLE_SET_VALUE, VARIABLE)
  }

  @Test
  fun testSetUnknownValueType() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_UNKNOWN_VALUE_TYPE)
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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_ESCAPE_SET_STRINGS)
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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_QUOTES_IN_STRING)
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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_BOTH_STRING_TYPES)
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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_GARBAGE_REFERENCE)
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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_REFERENCE_WITH_MODEL)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "and there before me was a white horse!", STRING, REGULAR, 0)

      val otherModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(otherModel, STRING_TYPE, "“Come and see!” I looked", STRING, REGULAR, 0)

      // Set prop2 to refer to prop1
      propertyModel.setValue(ReferenceTo(otherModel))
      verifyPropertyModel(propertyModel, STRING_TYPE, "ext.prop1", REFERENCE, REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)

    // Check the value
    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "ext.prop1", REFERENCE, REGULAR, 1)
    }
  }

  @Test
  fun testQuotesWithinQuotes() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_QUOTES_WITHIN_QUOTES)
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

    // Check its correct after a reparse.
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "\"Come and see!\" I looked", STRING, REGULAR, 0)
    }
  }

  @Test
  fun testSetReferenceValue() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_REFERENCE_VALUE)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop2", REFERENCE, REGULAR, 1)

      propertyModel.setValue(ReferenceTo("prop1"))
    }

    applyChangesAndReparse(buildModel)

    // Check the the reference has changed.
    run {
      val propertyModel = buildModel.ext().findProperty("prop3")
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)
    }
  }

  @Test
  fun testChangePropertyTypeToReference() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_CHANGE_PROPERTY_TYPE_TO_REFERENCE)
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

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 1)

      val unusedModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(unusedModel, STRING_TYPE, "25", STRING, REGULAR, 0)
    }
  }

  @Test
  fun testChangePropertyTypeToLiteral() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_CHANGE_PROPERTY_TYPE_TO_LITERAL)
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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DEPENDENCY_CHANGED_UPDATES_VALUE)
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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DEPENDENCY_BASIC_CYCLE)
    val buildModel = gradleBuildModel
    val propertyModel = buildModel.ext().findProperty("prop1")
    verifyPropertyModel(propertyModel, STRING_TYPE, "${'$'}{${extraName("prop1")}}", STRING, REGULAR, 0)
  }

  @Test
  fun testDependencyBasicCycleReference() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DEPENDENCY_BASIC_CYCLE_REFERENCE)
    val buildModel = gradleBuildModel
    val propertyModel = buildModel.ext().findProperty("prop1")
    verifyPropertyModel(propertyModel, STRING_TYPE, extraName("prop1"), REFERENCE, REGULAR, 0)
  }

  @Test
  fun testDependencyNoCycle4Depth() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DEPENDENCY_NO_CYCLE4_DEPTH)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop4")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Value", STRING, REGULAR, 1)
    }
  }

  @Test
  fun testDependencyTwice() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DEPENDENCY_TWICE)
    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(propertyModel, STRING_TYPE, "Value + Value", STRING, REGULAR, 2)
    }
  }

  @Test
  fun testDependencyNoCycle() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DEPENDENCY_NO_CYCLE)
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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_PROPERTY)
    val buildModel = gradleBuildModel

    // Delete the property
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      propertyModel.delete()
    }

    applyChangesAndReparse(buildModel)

    // Check everything has been deleted
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(NONE, propertyModel.valueType)
    }
  }

  @Test
  fun testDeleteVariable() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_VARIABLE)
    val buildModel = gradleBuildModel

    // Delete the property
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      propertyModel.delete()
    }

    applyChangesAndReparse(buildModel)

    // Check everything has been deleted
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(NONE, propertyModel.valueType)
    }
  }

  @Test
  fun testDeleteAndResetProperty() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_AND_RESET_PROPERTY)
    val buildModel = gradleBuildModel

    verifyDeleteAndResetProperty(buildModel)
  }

  @Test
  fun testDeleteAndResetKTSArrayExpressionProperty() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_AND_RESET_KTS_ARRAY_EXPRESSION_PROPERTY)
    val buildModel = gradleBuildModel

    verifyDeleteAndResetProperty(buildModel)
  }

  @Test
  fun testUpdatePropertyValueWithoutSyntaxChange() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_UPDATE_PROPERTY_WITHOUT_SYNTAX_CHANGE)
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

    verifyFileContents(myBuildFile, GRADLE_PROPERTY_MODEL_UPDATE_PROPERTY_WITHOUT_SYNTAX_CHANGE_EXPECTED)
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

    run {
      val propertyModel = buildModel.ext().findProperty("coolpropertyname")
      verifyPropertyModel(propertyModel, OBJECT_TYPE, null, NONE, REGULAR, 0)
    }
  }

  @Test
  fun testDeleteVariableDependency() {
    // This test is Groovy specific as it declares variables inside extra block.
    assumeTrue(isGroovy)
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_VARIABLE_DEPENDENCY)
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

    run {
      val firstPropertyModel = buildModel.ext().findProperty("prop2")
      verifyPropertyModel(firstPropertyModel, STRING_TYPE, "prop1", REFERENCE, REGULAR, 0)
    }
  }

  @Test
  fun testCheckSettingDeletedModel() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_CHECK_SETTING_DELETED_MODEL)
    val buildModel = gradleBuildModel

    // Delete the property and attempt to set it again.
    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      propertyModel.delete()
      propertyModel.setValue("New Value")

      verifyPropertyModel(propertyModel, STRING_TYPE, "New Value", STRING, REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_PROPERTY_IN_LIST)

    val buildModel = gradleBuildModel
    verifyDeletePropertyInList(buildModel)
  }

  @Test
  fun testDeleteArrayExpressionPropertyInList() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_ARRAY_EXPRESSION_PROPERTY_IN_LIST)

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

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(MAP, propertyModel.valueType)
      assertSize(0, propertyModel.getValue(MAP_TYPE)!!.entries)
    }
  }

  @Test
  fun testAddMapValueToString() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_ADD_MAP_VALUE_TO_STRING)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_NEW_VALUE_IN_MAP)

    val buildModel = gradleBuildModel
    verifySetNewValueInMap(buildModel)
  }

  @Test
  fun testSetNewValueInMapForKTSArrayExpression() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_NEW_VALUE_IN_MAP_FOR_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel
    verifySetNewValueInMap(buildModel)
  }

  @Test
  fun testSetNewValueInEmptyMap() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_NEW_VALUE_IN_EMPTY_MAP)
    val buildModel = gradleBuildModel

    verifySetNewValueInEmptyMap(buildModel)
  }

  @Test
  fun testSetNewValueInEmptyMapForKTSArrayExpression() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_NEW_VALUE_IN_EMPTY_MAP_FOR_KTS_ARRAY_EXPRESSION)
    val buildModel = gradleBuildModel

    verifySetNewValueInEmptyMap(buildModel)
  }

  @Test
  fun testDeletePropertyInMap() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_PROPERTY_IN_MAP)

    val buildModel = gradleBuildModel

    verifyDeletePropertyInMap(buildModel, GRADLE_PROPERTY_MODEL_DELETE_PROPERTY_IN_MAP_EXPECTED)
  }

  @Test
  fun testDeletePropertyInMapForKTSArrayExpression() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_PROPERTY_IN_MAP_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyDeletePropertyInMap(buildModel, GRADLE_PROPERTY_MODEL_DELETE_PROPERTY_IN_MAP_FOR_KTS_ARRAY_EXPRESSION_EXPECTED)
  }

  @Test
  fun testDeleteMapItemToAndSetFromEmpty() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_MAP_ITEM_TO_AND_SET_FROM_EMPTY)

    val buildModel = gradleBuildModel

    verifyDeleteMapItemToAndSetFromEmpty(buildModel)
  }

  @Test
  fun testDeleteMapItemToAndSetFromEmptyForKTSArrayExpression() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_MAP_ITEM_TO_AND_SET_FROM_EMPTY_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyDeleteMapItemToAndSetFromEmpty(buildModel)
  }

  @Test
  fun testSetMapValueToLiteral() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_MAP_VALUE_TO_LITERAL)

    val buildModel = gradleBuildModel

    verifySetMapValueToLiteralForKTSArrayExpression(buildModel)
  }

  @Test
  fun testSetMapValueToLiteralForKTSArrayExpression() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_MAP_VALUE_TO_LITERAL_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifySetMapValueToLiteralForKTSArrayExpression(buildModel)
  }

  @Test
  fun testDeleteToEmptyMap() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_TO_EMPTY_MAP)

    val buildModel = gradleBuildModel

    verifyDeleteToEmptyMap(buildModel)
  }

  @Test
  fun testDeleteToEmptyMapForKTSArrayExpression() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_TO_EMPTY_MAP_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyDeleteToEmptyMap(buildModel)
  }

  @Test
  fun testAddExistingMapProperty() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_ADD_EXISTING_MAP_PROPERTY)

    val buildModel = gradleBuildModel

    verifyAddExistingMapProperty(buildModel)
  }

  @Test
  fun testAddExistingMapPropertyForKTSArrayExpression() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_ADD_EXISTING_MAP_PROPERTY_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyAddExistingMapProperty(buildModel)
  }

  @Test
  fun testDeleteMapProperty() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_MAP_PROPERTY)

    val buildModel = gradleBuildModel

    verifyDeleteMapProperty(buildModel)
  }

  @Test
  fun testDeleteMapPropertyForKTSArrayExpression() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_MAP_PROPERTY_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyDeleteMapProperty(buildModel)
  }

  @Test
  fun testDeleteMapVariable() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_MAP_VARIABLE)

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

    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyPropertyModel(propertyModel, STRING_TYPE, "map", REFERENCE, REGULAR, 0)
    }
  }

  @Test
  fun testDeleteEmptyMap() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_EMPTY_MAP)

    val buildModel = gradleBuildModel

    verifyDeleteEmptyMap(buildModel)
  }

  @Test
  fun testDeleteEmptyMapForKTSArrayExpression() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_EMPTY_MAP_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyDeleteEmptyMap(buildModel)
  }

  @Test
  fun testSetLiteralToMapValue() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_LITERAL_TO_MAP_VALUE)

    val buildModel = gradleBuildModel

    verifySetLiteralToMapValue(buildModel)
  }

  @Test
  fun testSetLiteralToMapValueForKTSArrayExpression() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_LITERAL_TO_MAP_VALUE_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifySetLiteralToMapValue(buildModel)
  }

  @Test
  fun testParseMapInMap() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_PARSE_MAP_IN_MAP)

    val buildModel = gradleBuildModel

    verifyParseMapInMap(buildModel)
  }

  @Test
  fun testParseMapInMapForKTSArrayExpression() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_PARSE_MAP_IN_MAP_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyParseMapInMap(buildModel)
  }

  @Test
  fun testMapsInMap() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_MAPS_IN_MAP)

    val buildModel = gradleBuildModel

    verifyMapsInMap(buildModel, GRADLE_PROPERTY_MODEL_MAPS_IN_MAP_EXPECTED)
  }

  @Test
  fun testMapsInMapForKTSArrayExpression() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_MAPS_IN_MAP_FOR_KTS_ARRAY_EXPRESSION)

    val buildModel = gradleBuildModel

    verifyMapsInMap(buildModel, GRADLE_PROPERTY_MODEL_MAPS_IN_MAP_FOR_KTS_ARRAY_EXPRESSION_EXPECTED)
  }

  @Test
  fun testMapOrder() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_MAP_ORDER)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_MAP_IN_MAP)

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

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      assertEquals(LIST, propertyModel.valueType)
      val list = propertyModel.getValue(LIST_TYPE)
      assertSize(0, list)
    }
  }

  @Test
  fun testConvertToEmptyList() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_CONVERT_TO_EMPTY_LIST)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_ADD_TO_NONE_LIST)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_ADD_OUT_OF_BOUNDS)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_LIST_IN_MAP)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_TO_LIST_VALUES)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_ADD_SINGLE_ELEMENT_TO_EMPTY)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0)
      propertyModel.convertToEmptyList().addListValue().setValue("Good")

      verifyListProperty(propertyModel, listOf("Good"), REGULAR, 0)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf("Good"), REGULAR, 0)
    }
  }

  @Test
  fun testAddToAndDeleteListFromEmpty() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_ADD_TO_AND_DELETE_LIST_FROM_EMPTY)

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
      verifyPropertyModel(list[5].dependencies[0], INTEGER_TYPE, 6, INTEGER, VARIABLE, 0, "six", "ext.six")

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_ADD_AND_REMOVE_FROM_NON_LITERAL_LIST)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_LIST)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_ADD_MIDDLE_OF_LIST)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, 4), REGULAR, 0)

      propertyModel.addListValueAt(1).setValue(ReferenceTo("var1"))
      propertyModel.addListValueAt(2).setValue(3)

      verifyListProperty(propertyModel, listOf(1, "2", 3, 4), REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, "2", 3, 4), REGULAR, 1)
    }
  }

  @Test
  fun testSetInMiddleOfList() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_IN_MIDDLE_OF_LIST)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, 2, "2", 4), REGULAR, 1)

      propertyModel.getValue(LIST_TYPE)!![1].setValue(ReferenceTo("var1"))
      propertyModel.getValue(LIST_TYPE)!![2].setValue(3)

      verifyListProperty(propertyModel, listOf(1, "2", 3, 4), REGULAR, 1)
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyListProperty(propertyModel, listOf(1, "2", 3, 4), REGULAR, 1)
    }
  }

  @Test
  fun testResolveAndSetVariablesInParentModule() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_RESOLVE_AND_SET_VARIABLES_IN_PARENT_MODULE)
    writeToSubModuleBuildFile(GRADLE_PROPERTY_MODEL_RESOLVE_VARIABLES_IN_PARENT_MODULE_SUB)
    writeToSettingsFile(subModuleSettingsText)

    val buildModel = subModuleGradleBuildModel

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_RESOLVE_VARIABLES_IN_PROPERTIES_FILE)
    writeToSubModuleBuildFile(GRADLE_PROPERTY_MODEL_RESOLVE_VARIABLES_IN_PROPERTIES_FILE_SUB)
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

      // Properties file can't be edited directed.
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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_VALUE_IN_MAP)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_MAP_VALUE_ON_NONE_MAP)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_OUTER_SCOPE_VARIABLES_RESOLVED)

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

    run {
      val defaultConfig = buildModel.android().defaultConfig()
      verifyPropertyModel(defaultConfig.minSdkVersion(), INTEGER_TYPE, 18, INTEGER, REGULAR, 1)
      verifyPropertyModel(defaultConfig.targetSdkVersion(), INTEGER_TYPE, 21, INTEGER, REGULAR, 1)
    }
  }

  @Test
  fun testInScopeElement() {
    val childProperties = "prop3 = chickadee"
    val parentProperties = "prop4 = ferret\nnested.prop5 = narwhal"
    writeToBuildFile(GRADLE_PROPERTY_MODEL_IN_SCOPE_ELEMENT)
    writeToSubModuleBuildFile(GRADLE_PROPERTY_MODEL_IN_SCOPE_ELEMENT_SUB)
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
    val b = writeToNewProjectFile("b", GRADLE_PROPERTY_MODEL_VARIABLES_FROM_NESTED_APPLY_APPLIED_FILE_ONE)
    val a = writeToNewProjectFile("a", GRADLE_PROPERTY_MODEL_VARIABLES_FROM_NESTED_APPLY_APPLIED_FILE_TWO)
    writeToBuildFile(GRADLE_PROPERTY_MODEL_VARIABLES_FROM_NESTED_APPLY)

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

  @Ignore
  @Test
  fun testApplicationCycle() {
    writeToNewProjectFile("a", GRADLE_PROPERTY_MODEL_APPLICATION_CYCLE_APPLIED)
    writeToBuildFile(GRADLE_PROPERTY_MODEL_APPLICATION_CYCLE)

    // Make sure we don't blow up.
    val buildModel = gradleBuildModel

    run {
      val properties = buildModel.ext().inScopeProperties
      assertSize(2, properties.values)
    }
  }

  @Test
  fun testVariablesFromApply() {
    writeToNewProjectFile("vars", GRADLE_PROPERTY_MODEL_VARIABLES_FROM_APPLY_APPLIED)
    writeToBuildFile(GRADLE_PROPERTY_MODEL_VARIABLES_FROM_APPLY)

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
    ApplicationManager.getApplication().runWriteAction { PlatformTestUtil.getOrCreateProjectBaseDir(myProject).fileSystem.refresh(false) }

    run {
      val propertyModel = buildModel.ext().findProperty("prop4")
      verifyPropertyModel(propertyModel, STRING_TYPE, "72 : 72 world! : true", STRING, REGULAR, 3)
    }
  }

  @Test
  fun testAddRemoveReferenceValues() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_ADD_REMOVE_REFERENCE_VALUES)

    val buildModel = gradleBuildModel
    var extModel = buildModel.ext()

    run {
      val propertyModel = extModel.findProperty("propList")
      verifyListProperty(propertyModel, listOf("1", "2", "3", "2", "2nd"), REGULAR, 4)
      propertyModel.toList()!![0].setValue(ReferenceTo("propC"))
      verifyListProperty(propertyModel, listOf("3", "2", "3", "2", "2nd"), REGULAR, 5)
    }

    applyChangesAndReparse(buildModel)
    extModel = buildModel.ext()

    run {
      val propertyModel = extModel.findProperty("propList")
      verifyListProperty(propertyModel, listOf("3", "2", "3", "2", "2nd"), REGULAR, 5)
    }
  }

  @Test
  fun testRename() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_RENAME)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "hello ${'$'}{var2}", STRING, REGULAR, 1, "prop1", "ext.prop1")
      val varModel = propertyModel.dependencies[0]
      verifyPropertyModel(varModel, STRING_TYPE, "hello", STRING, VARIABLE, 0, "var1", "ext.var1")

      // Rename the properties.
      propertyModel.rename("prop2")
      varModel.rename("var2")

      verifyPropertyModel(propertyModel, STRING_TYPE, "${'$'}{var1} hello", STRING, REGULAR, 1, "prop2", "ext.prop2")
      verifyPropertyModel(varModel, STRING_TYPE, "hello", STRING, VARIABLE, 0, "var2", "ext.var2")
    }

    applyChangesAndReparse(buildModel)

    run {
      val propertyModel = buildModel.ext().findProperty("prop2")
      val varModel = propertyModel.dependencies[0]
      verifyPropertyModel(propertyModel, STRING_TYPE, "${'$'}{var1} hello", STRING, REGULAR, 1, "prop2", "ext.prop2")
      verifyPropertyModel(varModel, STRING_TYPE, "hello", STRING, VARIABLE, 0, "var2", "ext.var2")
    }
  }

  @Test
  fun testRenameMapPropertyAndKeys() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_RENAME_MAP_PROPERTY_AND_KEYS)

    val buildModel = gradleBuildModel
    run {
      val firstMapModel = buildModel.ext().findProperty("map1")
      verifyMapProperty(firstMapModel, mapOf("key1" to "a", "key2" to "b", "key3" to "c"), "map1", "ext.map1")
      val secondMapModel = buildModel.ext().findProperty("map2")
      verifyMapProperty(secondMapModel, mapOf("key4" to 4), "map2", "ext.map2")

      // Rename the keys
      val firstKeyModel = firstMapModel.getMapValue("key2")
      verifyPropertyModel(firstKeyModel, STRING_TYPE, "b", STRING, DERIVED, 0, "key2", "ext.map1.key2")
      val secondKeyModel = secondMapModel.getMapValue("key4")
      verifyPropertyModel(secondKeyModel, INTEGER_TYPE, 4, INTEGER, DERIVED, 0, "key4", "ext.map2.key4")

      firstKeyModel.rename("newKey1")
      secondKeyModel.rename("newKey2")

      // Rename the maps
      firstMapModel.rename("newMap1")
      secondMapModel.rename("newMap2")

      verifyMapProperty(firstMapModel, mapOf("key1" to "a", "newKey1" to "b", "key3" to "c"), "newMap1", "ext.newMap1")
      verifyMapProperty(secondMapModel, mapOf("newKey2" to 4), "newMap2", "ext.newMap2")
      verifyPropertyModel(firstKeyModel, STRING_TYPE, "b", STRING, DERIVED, 0, "newKey1", "ext.newMap1.newKey1")
      verifyPropertyModel(secondKeyModel, INTEGER_TYPE, 4, INTEGER, DERIVED, 0, "newKey2", "ext.newMap2.newKey2")
    }

    applyChangesAndReparse(buildModel)

    run {
      val firstMapModel = buildModel.ext().findProperty("newMap1")
      verifyMapProperty(firstMapModel, mapOf("key1" to "a", "newKey1" to "b", "key3" to "c"), "newMap1", "ext.newMap1")
      val secondMapModel = buildModel.ext().findProperty("newMap2")
      verifyMapProperty(secondMapModel, mapOf("newKey2" to 4), "newMap2", "ext.newMap2")

      // Rename the keys
      val firstKeyModel = firstMapModel.getMapValue("newKey1")
      verifyPropertyModel(firstKeyModel, STRING_TYPE, "b", STRING, DERIVED, 0, "newKey1", "ext.newMap1.newKey1")
      val secondKeyModel = secondMapModel.getMapValue("newKey2")
      verifyPropertyModel(secondKeyModel, INTEGER_TYPE, 4, INTEGER, DERIVED, 0, "newKey2", "ext.newMap2.newKey2")
    }
  }

  @Test
  fun testRenameListValueThrows() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_RENAME_LIST_VALUE_THROWS)

    val buildModel = gradleBuildModel
    run {
      val firstListModel = buildModel.ext().findProperty("list1")
      verifyListProperty(firstListModel, listOf(1, 2, 3, 4), VARIABLE, 0, "list1", "ext.list1")
      val secondListModel = buildModel.ext().findProperty("list2")
      verifyListProperty(secondListModel, listOf("a", "b", "c", "d"), REGULAR, 0, "list2", "ext.list2")

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

      verifyListProperty(firstListModel, listOf(1, 2, 3, 4), VARIABLE, 0, "varList", "ext.varList")
      verifyListProperty(secondListModel, listOf("a", "b", "c", "d"), REGULAR, 0, "propertyList", "ext.propertyList")
    }

    applyChangesAndReparse(buildModel)

    run {
      val firstListModel = buildModel.ext().findProperty("varList")
      verifyListProperty(firstListModel, listOf(1, 2, 3, 4), VARIABLE, 0, "varList", "ext.varList")
      val secondListModel = buildModel.ext().findProperty("propertyList")
      verifyListProperty(secondListModel, listOf("a", "b", "c", "d"), REGULAR, 0, "propertyList", "ext.propertyList")
    }
  }

  @Test
  fun testGetDeclaredProperties() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_GET_DECLARED_PROPERTIES)

    val buildModel = gradleBuildModel
    val extProperties = buildModel.ext().declaredProperties
    val androidProperties = buildModel.android().declaredProperties
    val debugProperties = buildModel.android().buildTypes()[0].declaredProperties

    assertSize(2, extProperties)
    assertSize(3, androidProperties)
    assertSize(2, debugProperties)

    verifyPropertyModel(extProperties[0], STRING_TYPE, "property", STRING, REGULAR, 0, "prop1")
    verifyPropertyModel(extProperties[1], STRING_TYPE, "value", STRING, VARIABLE, 0, "var")
    verifyPropertyModel(androidProperties[0], STRING_TYPE, "Spooky", STRING, VARIABLE, 0, "outerVar")
    verifyPropertyModel(androidProperties[1], STRING_TYPE, "sneaky", REFERENCE, VARIABLE, 0, "var")
    verifyPropertyModel(androidProperties[2], BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, BuildTypeModelImpl.MINIFY_ENABLED)
    verifyPropertyModel(debugProperties[0], STRING_TYPE, "sneaky", REFERENCE, VARIABLE, 0, "var")
    verifyPropertyModel(debugProperties[1], BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, BuildTypeModelImpl.MINIFY_ENABLED)
  }

  @Test
  fun testDeleteItemsFromList() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_ITEMS_FROM_LIST)

    val buildModel = gradleBuildModel
    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyListProperty(propertyModel, "ext.prop", listOf(1))
      val itemModel = propertyModel.toList()!![0]
      itemModel.delete()
    }

    applyChangesAndReparse(buildModel)

    verifyListProperty(buildModel.ext().findProperty("prop"), "ext.prop", listOf())
  }

  @Test
  fun testDeleteListWithItems() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_LIST_WITH_ITEMS)

    val buildModel = gradleBuildModel
    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      propertyModel.delete()
    }

    applyChangesAndReparse(buildModel)

    assertMissingProperty(buildModel.ext().findProperty("prop"))
  }

  @Test
  fun testDeleteItemsInMap() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_ITEMS_IN_MAP)

    val buildModel = gradleBuildModel
    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      verifyMapProperty(propertyModel, mapOf("key" to 1))
      val itemModel = propertyModel.toMap()!!["key"]!!
      itemModel.delete()
    }

    applyChangesAndReparse(buildModel)

    verifyMapProperty(buildModel.ext().findProperty("prop"), mapOf())
  }

  @Test
  fun testDeleteMapWithItems() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DELETE_MAP_WITH_ITEMS)

    val buildModel = gradleBuildModel
    run {
      val propertyModel = buildModel.ext().findProperty("prop")
      propertyModel.delete()
    }

    applyChangesAndReparse(buildModel)

    assertMissingProperty(buildModel.ext().findProperty("prop"))
  }

  private fun runSetPropertyTest(fileName: TestFileName, type: PropertyType) {
    writeToBuildFile(fileName)

    val buildModel = gradleBuildModel

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(propertyModel, STRING_TYPE, "value", STRING, type, 0)
      val oldGradleFile = propertyModel.gradleFile

      val stringValue = "Hello world!"
      propertyModel.setValue(stringValue)
      verifyPropertyModel(propertyModel, STRING_TYPE, stringValue, STRING, type, 0)
      applyChangesAndReparse(buildModel)
      val newStringModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(newStringModel, STRING_TYPE, stringValue, STRING, type, 0)
      assertEquals(oldGradleFile, newStringModel.gradleFile)
    }

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      val intValue = 26
      propertyModel.setValue(intValue)
      verifyPropertyModel(propertyModel, INTEGER_TYPE, intValue, INTEGER, type, 0)
      applyChangesAndReparse(buildModel)
      val newIntModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(newIntModel, INTEGER_TYPE, intValue, INTEGER, type, 0)
    }

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      val boolValue = true
      propertyModel.setValue(boolValue)
      verifyPropertyModel(propertyModel, BOOLEAN_TYPE, boolValue, BOOLEAN, type, 0)
      applyChangesAndReparse(buildModel)
      val newBooleanModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(newBooleanModel, BOOLEAN_TYPE, boolValue, BOOLEAN, type, 0)
    }

    run {
      val propertyModel = buildModel.ext().findProperty("prop1")
      val refValue = "\"${'$'}{prop2}\""
      propertyModel.setValue(refValue)
      // Resolved value and dependencies are only updated after the model has been applied and re-parsed.
      verifyPropertyModel(propertyModel, STRING_TYPE, "ref", STRING, type, 1)
      assertEquals("${'$'}{prop2}", propertyModel.getRawValue(STRING_TYPE))
      applyChangesAndReparse(buildModel)
      val newRefModel = buildModel.ext().findProperty("prop1")
      verifyPropertyModel(newRefModel, STRING_TYPE, "ref", STRING, type, 1)
      assertEquals("${'$'}{prop2}", newRefModel.getRawValue(STRING_TYPE))
    }
  }

  @Test
  fun testObtainExpressionPsiElement() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_OBTAIN_EXPRESSION_PSI_ELEMENT)

    val buildModel = gradleBuildModel
    val extModel = buildModel.ext()

    assertThat(extModel.findProperty("prop1").expressionPsiElement!!.text, equalTo("'value'"))
    assertThat(extModel.findProperty("prop1").fullExpressionPsiElement!!.text, equalTo("'value'"))
    assertThat(extModel.findProperty("prop2").expressionPsiElement!!.text, equalTo("25"))
    assertThat(extModel.findProperty("prop2").fullExpressionPsiElement!!.text, equalTo("25"))
    assertThat(extModel.findProperty("prop3").expressionPsiElement!!.text, equalTo("true"))
    assertThat(extModel.findProperty("prop3").fullExpressionPsiElement!!.text, equalTo("true"))
    assertThat(extModel.findProperty("prop4").expressionPsiElement!!.text, equalTo("[\"key\": 'val']"))
    assertThat(extModel.findProperty("prop4").fullExpressionPsiElement!!.text, equalTo("[\"key\": 'val']"))
    assertThat(extModel.findProperty("prop5").expressionPsiElement!!.text, equalTo("['val1', 'val2', \"val3\"]"))
    assertThat(extModel.findProperty("prop5").fullExpressionPsiElement!!.text, equalTo("['val1', 'val2', \"val3\"]"))
    assertThat(extModel.findProperty("prop6").expressionPsiElement!!.text, equalTo("25.3"))
    assertThat(extModel.findProperty("prop6").fullExpressionPsiElement!!.text, equalTo("25.3"))

    val mapItem = extModel.findProperty("prop4").getMapValue("key")
    val listItem = extModel.findProperty("prop5").getListValue("val2")!!
    assertThat(mapItem.expressionPsiElement!!.text, equalTo("'val'"))
    assertThat(mapItem.fullExpressionPsiElement!!.text, equalTo("'val'"))
    assertThat(listItem.expressionPsiElement!!.text, equalTo("'val2'"))
    assertThat(listItem.fullExpressionPsiElement!!.text, equalTo("'val2'"))

    val configModel = buildModel.android().signingConfigs()[0]!!
    assertThat(configModel.storeFile().expressionPsiElement!!.text, equalTo("'my_file.txt'"))
    assertThat(configModel.storeFile().fullExpressionPsiElement!!.text, equalTo("file('my_file.txt')"))
    assertThat(configModel.storePassword().expressionPsiElement!!.text, equalTo("\"KSTOREPWD\""))
    assertThat(configModel.storePassword().fullExpressionPsiElement!!.text, equalTo("System.getenv(\"KSTOREPWD\")"))
  }

  @Test
  fun testAddToVariable() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_ADD_TO_VARIABLE)

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
    assumeTrue(isGroovy())
    writeToBuildFile("")

    val buildModel = gradleBuildModel
    val newProperty = buildModel.ext().findProperty("var")
    newProperty.setValue(ReferenceTo("var"))

    verifyPropertyModel(newProperty, STRING_TYPE, "var", REFERENCE, REGULAR, 0)
  }

  /**
   * Tests to ensure that references return the ReferenceTo type when getRawValue is called with either
   * OBJECT_TYPE or REFERENCE_TO_TYPE.
   */
  @Test
  fun testReferenceToReturnObject() {
    writeToBuildFile(GRADLE_PROPERTY_MODEL_REFERENCE_TO_RETURN_OBJECT)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_SET_REFERENCE_VALUE)

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
    writeToBuildFile(GRADLE_PROPERTY_MODEL_DUPLICATE_MAP_KEY)

    val buildModel = gradleBuildModel
    val map = buildModel.ext().findProperty("versions").toMap()!!
    assertSize(1, map.keys)
    assertThat(map["firebasePlugins"]!!.forceString(), equalTo("2.1.5"))
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
}
