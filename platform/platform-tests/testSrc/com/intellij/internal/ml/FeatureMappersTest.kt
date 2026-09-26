// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml

import org.junit.Test
import kotlin.test.assertEquals

class FeatureMappersTest {
  companion object {
    private val binary = BinaryFeature("is_in_same_file",
                                       "true" to 1.0,
                                       "false" to 0.0,
                                       10.0, true)
    private val float = FloatFeature("position", 3.0, true)
    private val floatWithoutUndefined = FloatFeature("duration", 300.0, false)
    private val simpleCategoricalFeature = CategoricalFeature("visibility", setOf("public", "private"))
    private val categoricalWithOther = CategoricalFeature("color", setOf("red", "green", "blue", CategoricalFeature.OTHER))
    private val categoricalWithUndefined = CategoricalFeature("kind", setOf("keyword", "method", "field", Feature.UNDEFINED))
    private val categoricalWithOtherAndUndefined = CategoricalFeature("language",
                                                                      setOf("eng", "ru", CategoricalFeature.OTHER, Feature.UNDEFINED))
  }

  @Test
  fun `mappers should return the same feature name`() {
    fun check(feature: Feature, mapper: FeatureMapper) {
      assertEquals(feature.name, mapper.featureName, "Mapper should return the same name")
    }

    check(binary, binary.createMapper(null))
    check(binary, binary.createMapper(Feature.UNDEFINED))

    check(float, float.createMapper(null))
    check(float, float.createMapper(Feature.UNDEFINED))

    check(categoricalWithOther, categoricalWithOther.createMapper("green"))
    check(categoricalWithOther, categoricalWithOther.createMapper("blue"))
    check(categoricalWithOther, categoricalWithOther.createMapper(CategoricalFeature.OTHER))
  }

  @Test
  fun `binary feature should handle boolean values`() {
    assertEquals(binary.firstValueMapping.second, binary.createMapper(null).asArrayValue(true))
    assertEquals(binary.secondValueMapping.second, binary.createMapper(null).asArrayValue(false))
    assertEquals(binary.defaultValue, binary.createMapper(null).asArrayValue(100))
  }

  @Test
  fun `binary feature should be case insensitive`() {
    val binaryCaseSensitive = BinaryFeature("is_in_same_file_case_sensitive",
                                            "True" to 1.0,
                                            "False" to 0.0,
                                            10.0, true)
    assertEquals(binaryCaseSensitive.firstValueMapping.second, binaryCaseSensitive.createMapper(null).asArrayValue(true))
    assertEquals(binaryCaseSensitive.secondValueMapping.second, binaryCaseSensitive.createMapper(null).asArrayValue(false))
  }

  @Test
  fun `categorical feature should be case insensitive`() {
    val mapper1 = categoricalWithOther.createMapper("red")
    assertEquals(mapper1.asArrayValue("yellow"), mapper1.asArrayValue("Yellow"))
    assertEquals(mapper1.asArrayValue("red"), mapper1.asArrayValue("RED"))
    assertEquals(0.0, mapper1.asArrayValue(null))
  }

  @Test(expected = InconsistentMetadataException::class)
  fun `categorical feature should declare categories`() {
    categoricalWithOther.createMapper(null)
  }

  @Test
  fun `test undefined mapper`() {
    fun checkUndefinedMapper(feature: Feature, definedValue: Any) {
      val mapper = feature.createMapper(Feature.UNDEFINED)
      assertEquals(1.0, mapper.asArrayValue(null), "undefined mapped must map null to 1.0")
      assertEquals(0.0, mapper.asArrayValue(definedValue), "undefined mapped must map not null to 0.0")
    }

    checkUndefinedMapper(binary, binary.firstValueMapping.second)
    checkUndefinedMapper(float, 100.0)
    checkUndefinedMapper(categoricalWithUndefined, "")
    checkUndefinedMapper(categoricalWithOtherAndUndefined, "eng")
  }

  @Test
  fun `test binary mapper`() {
    val mapper = binary.createMapper(null)
    assertEquals(binary.firstValueMapping.second, mapper.asArrayValue(binary.firstValueMapping.first))
    assertEquals(binary.secondValueMapping.second, mapper.asArrayValue(binary.secondValueMapping.first))
    assertEquals(binary.defaultValue, mapper.asArrayValue(null))
  }

  @Test
  fun `binary should return default value if unknown`() {
    assertEquals(binary.defaultValue, binary.createMapper(null).asArrayValue("unknown_value"))
  }

  @Test
  fun `float feature should recognize string values`() {
    val mapper = float.createMapper(null)
    assertEquals(10.0, mapper.asArrayValue(10.0))
    assertEquals(10.0, mapper.asArrayValue("10"))
    assertEquals(10.0, mapper.asArrayValue("10.000"))
    assertEquals(10.0, mapper.asArrayValue("1E1"))
  }

  @Test(expected = InconsistentMetadataException::class)
  fun `float feature without undefined should not create undefined mapper`() {
    floatWithoutUndefined.createMapper(Feature.UNDEFINED)
  }

  @Test(expected = InconsistentMetadataException::class)
  fun `categorical feature should notice unexpected other`() {
    simpleCategoricalFeature.createMapper(CategoricalFeature.OTHER)
  }

  @Test(expected = InconsistentMetadataException::class)
  fun `categorical feature should notice unexpected undefined`() {
    categoricalWithOther.createMapper(Feature.UNDEFINED)
  }

  @Test
  fun `test other category mapper`() {
    val mapper1 = categoricalWithOther.createMapper(CategoricalFeature.OTHER)
    assertEquals(1.0, mapper1.asArrayValue("yellow"))
    assertEquals(0.0, mapper1.asArrayValue("red"))
    assertEquals(0.0, mapper1.asArrayValue(null))

    val mapper2 = categoricalWithOtherAndUndefined.createMapper(CategoricalFeature.OTHER)
    assertEquals(0.0, mapper2.asArrayValue("eng"))
    assertEquals(1.0, mapper2.asArrayValue("uk"))
    assertEquals(0.0, mapper2.asArrayValue(null))
  }

  @Test
  fun `test category mapper`() {
    val mapper = simpleCategoricalFeature.createMapper("public")
    assertEquals(1.0, mapper.asArrayValue("public"))
    assertEquals(0.0, mapper.asArrayValue("private"))
    assertEquals(0.0, mapper.asArrayValue("null"))
  }

  @Test
  fun `categorical mapper should recognize enum elements`() {
    val mapper = simpleCategoricalFeature.createMapper("public")
    assertEquals(1.0, mapper.asArrayValue(Access.public))
    assertEquals(0.0, mapper.asArrayValue(Access.private))
    assertEquals(0.0, mapper.asArrayValue(null))
  }

  @Suppress("EnumEntryName")
  private enum class Access {
    private, public
  }
}