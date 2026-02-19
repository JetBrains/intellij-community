package com.intellij.cce.actions.simplified

import com.intellij.cce.actions.ActionArraySerializer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DatasetTransformerTest {

  @Test
  fun `test transform JSON before and after`() {
    val sampleJson = """
      [
          {
              "openFiles": ["src/main/java/com/jetbrains/eval/Point.java"],
              "position": {
                  "path": "src/main/java/com/jetbrains/eval/Utils.java",
                  "caretLine": 3
              },
              "userPrompt": "add method to check email string",
              "fileValidations": [
                  {
                      "path": "src/main/java/com/jetbrains/eval/Utils.java",
                      "patterns": ["static.*boolean.*mail.*String"],
                      "changedLines": ["5-6"],
                      "unchangedLines": ["1-3"]
                  }
              ],
              "referenceImplementation": "patches/generate-email-method.patch"
         },
          {
              "openFiles": ["src/main/java/com/jetbrains/eval/Point.java"],
              "position": {
                  "path": "src/main/java/com/jetbrains/eval/Utils.java",
                  "selectionLines": "1-4"
              },
              "userPrompt": "add method to check email string",
              "fileValidations": [
                  {
                      "path": "src/main/java/com/jetbrains/eval/Utils.java",
                      "patterns": ["static.*boolean.*mail.*String"],
                      "changedLines": ["5-6"],
                      "unchangedLines": ["1-3"]
                  }
              ],
              "referenceImplementation": "patches/generate-email-method.patch"
          }
      ]
    """.trimIndent()

    val expectedJson = """
      [
        {
          "path": "src/main/java/com/jetbrains/eval/Utils.java",
          "sessionsCount": 2,
          "actions": [
            {
              "sessionId": "5584a08f-c83b-3a76-89bd-fd9c22d47bd3",
              "file": "src/main/java/com/jetbrains/eval/Point.java",
              "type": "OPEN_FILE_IN_BACKGROUND"
            },
            {
              "sessionId": "5584a08f-c83b-3a76-89bd-fd9c22d47bd3",
              "offset": 30,
              "type": "MOVE_CARET"
            },
            {
              "sessionId": "5584a08f-c83b-3a76-89bd-fd9c22d47bd3",
              "expectedText": "",
              "offset": 30,
              "nodeProperties": {
                "tokenType": "FILE",
                "location": "PROJECT",
                "features": [],
                "additional": {
                  "prompt": "add method to check email string",
                  "file_validations": "[{\"path\":\"src/main/java/com/jetbrains/eval/Utils.java\",\"patterns\":[\"static.*boolean.*mail.*String\"],\"changedLines\":[\"5-6\"],\"unchangedLines\":[\"1-3\"]}]"
                }
              },
              "type": "CALL_FEATURE"
            },
            {
              "sessionId": "1b197f31-2de6-3081-bcc0-cd3ace88c324",
              "file": "src/main/java/com/jetbrains/eval/Point.java",
              "type": "OPEN_FILE_IN_BACKGROUND"
            },
            {
              "sessionId": "1b197f31-2de6-3081-bcc0-cd3ace88c324",
              "begin": 10,
              "end": 40,
              "type": "SELECT_RANGE"
            },
            {
              "sessionId": "1b197f31-2de6-3081-bcc0-cd3ace88c324",
              "expectedText": "",
              "offset": 10,
              "nodeProperties": {
                "tokenType": "FILE",
                "location": "PROJECT",
                "features": [],
                "additional": {
                  "prompt": "add method to check email string",
                  "file_validations": "[{\"path\":\"src/main/java/com/jetbrains/eval/Utils.java\",\"patterns\":[\"static.*boolean.*mail.*String\"],\"changedLines\":[\"5-6\"],\"unchangedLines\":[\"1-3\"]}]"
                }
              },
              "type": "CALL_FEATURE"
            }
          ]
        }
      ]
    """.trimIndent()

    val offsetProvider = object : OffsetProvider {
      override fun getLineStartOffset(filePath: String, line: Int): Int = line * 10
      override fun getLineEndOffset(filePath: String, line: Int): Int = line * 10
    }

    val transformer = DatasetTransformer(offsetProvider)
    val interactions = SimplifiedDatasetSerializer.parseJson(sampleJson)
    val transformed = transformer.transform(interactions.toList())

    val actualJson = ActionArraySerializer.serialize(transformed.toTypedArray())
    Assertions.assertEquals(expectedJson, actualJson)
  }
}