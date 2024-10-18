// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.yaml

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase
import org.jetbrains.yaml.schema.YamlJsonSchemaHighlightingInspection
import java.util.function.Predicate

internal class GitlabYamlJsonSchemaValidationTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(YamlJsonSchemaHighlightingInspection::class.java)
    JsonSchemaHighlightingTestBase.registerJsonSchema(myFixture, """
      {
        "${"$"}schema": "http://json-schema.org/draft-07/schema#",
        "properties": {
          "script": {
            "oneOf": [
              {
                "type": "string",
                "minLength": 1
              },
              {
                "type": "array",
                "items": {
                  "anyOf": [
                    {
                      "type": "string"
                    },
                    {
                      "type": "array",
                      "items": {
                        "type": "string"
                      }
                    }
                  ]
                },
                "minItems": 1
              }
            ]
          }
        }
      }
    """.trimIndent(), ".json", Predicate { true })
  }

  fun `test custom gitlab ci yaml tags does not produce warnings`() {
    myFixture.configureByText(".gitlab-ci.yml", """
      script:
        - !reference [.setup, script]
    """.trimIndent())
    myFixture.checkHighlighting(true, false, true)
  }

  fun `test unknown tag produces warning`() {
    myFixture.configureByText(".gitlab-ci.yml", """
      script:
        - <warning descr="Schema validation: Incompatible types.
       Required one of: array, string.">!unknownTag [.setup, script]</warning>
    """.trimIndent())
    myFixture.checkHighlighting(true, false, true)
  }
}