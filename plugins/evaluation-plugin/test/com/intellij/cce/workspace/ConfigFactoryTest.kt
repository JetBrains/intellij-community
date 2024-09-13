package com.intellij.cce.workspace

import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.filter.EvaluationFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Type

class ConfigFactoryTest {

  @Test
  fun `test legacy format deserialization`() {

    deserialize(
      """
      {
        "outputDir": "outputDir",
        "strategy": {}
      }
    """.trimIndent()
    ).also {
      assertNull(it.actions)
    }

    assertThrows<IllegalStateException> {
      deserialize(
        """
        {
          "outputDir": "outputDir",
          "strategy": {},
          "language": "LANG"
        }
      """.trimIndent()
      )
    }

    assertThrows<IllegalStateException> {
      deserialize(
        """
        {
          "outputDir": "outputDir",
          "strategy": {},
          "projectPath": "projectPath"
        }
      """.trimIndent()
      )
    }

    assertThrows<IllegalStateException> {
      deserialize(
        """
        {
          "outputDir": "outputDir",
          "strategy": {},
          "projectName": "projectName"
        }
      """.trimIndent()
      )
    }


    deserialize(
      """
      {
        "outputDir": "outputDir",
        "strategy": {},
        "language": "LANG",
        "projectPath": "projectPath",
        "projectName": "projectName",
        "actions": {
          "evaluationRoots": []
        }
      }
      """.trimIndent()
    ).also {
      assertEquals("LANG", it.actions?.language)
      assertEquals("projectPath", it.actions?.projectPath)
      assertEquals("projectName", it.actions?.projectName)
    }
  }

  private fun deserialize(text: String) =
    ConfigFactory.deserialize(ConfigFactory.createGson(TestStrategySerializer), text, TestStrategySerializer)

  private object TestStrategy : EvaluationStrategy {
    override val filters: Map<String, EvaluationFilter> = emptyMap()
  }

  private object TestStrategySerializer : StrategySerializer<TestStrategy> {
    override fun serialize(src: TestStrategy, typeOfSrc: Type, context: JsonSerializationContext): JsonObject = JsonObject()
    override fun deserialize(map: Map<String, Any>, language: String): TestStrategy = TestStrategy
  }
}