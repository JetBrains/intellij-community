package com.intellij.configurationScript

import com.intellij.codeInsight.completion.CompletionTestCase
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.json.JsonFileType
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.jetbrains.jsonSchema.impl.JsonSchemaCompletionContributor
import com.jetbrains.jsonSchema.impl.JsonSchemaReader
import org.intellij.lang.annotations.Language
import java.nio.charset.StandardCharsets

class IntelliJConfigurationSchemaTest : CompletionTestCase() {
  companion object {
    private val schemaFile by lazy {
      LightVirtualFile("scheme.json", JsonFileType.INSTANCE, generateConfigurationSchema(), StandardCharsets.UTF_8, 0)
    }
  }

  fun `test map and description`() {
    val variants = test("""
    runConfigurations:
      jvmMainMethod:
        <caret>
    """.trimIndent())

    val variant = variants.first { it.lookupString == "env" }
    val presentation = LookupElementPresentation()
    variant.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo("Environment variables")
  }

  private fun test(@Language("YAML") text: String): List<LookupElement> {
    val position = EditorTestUtil.getCaretPosition(text)
    assertThat(position).isGreaterThan(0)

    val file = createFile(myModule, "intellij.yaml", text.replace("<caret>", "IntelliJIDEARulezzz"))
    val element = file.findElementAt(position)
    assertThat(element).isNotNull

    val schemaObject = JsonSchemaReader.readFromFile(myProject, schemaFile)
    assertThat(schemaObject).isNotNull

    return JsonSchemaCompletionContributor.getCompletionVariants(schemaObject, element!!, element)
  }
}