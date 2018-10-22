package com.intellij.configurationScript

import com.intellij.codeInsight.completion.CompletionTestCase
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.json.JsonFileType
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.jetbrains.jsonSchema.impl.JsonSchemaCompletionContributor
import com.jetbrains.jsonSchema.impl.JsonSchemaReader
import org.intellij.lang.annotations.Language
import java.nio.charset.StandardCharsets

// this test requires YamlJsonSchemaCompletionContributor, that's why intellij.yaml is added as test dependency
internal class ConfigurationSchemaTest : CompletionTestCase() {
  private var schemaFile: LightVirtualFile? = null
  override fun setUp() {
    super.setUp()
    schemaFile = LightVirtualFile("scheme.json", JsonFileType.INSTANCE, generateConfigurationSchema(), StandardCharsets.UTF_8, 0)
  }

  fun `test map and description`() {
    val variants = test("""
    runConfigurations:
      jvmMainMethod:
        <caret>
    """.trimIndent())

    checkDescription(variants, "env", "Environment variables")
    checkDescription(variants, "isAllowRunningInParallel", "Allow running in parallel")
    checkDescription(variants, "isShowConsoleOnStdErr", "Show console when a message is printed to standard error stream")
    checkDescription(variants, "isShowConsoleOnStdOut", "Show console when a message is printed to standard output stream")
  }

  fun `test no isAllowRunningInParallel if singleton policy not configurable`() {
    val variants = test("""
    runConfigurations:
      compound:
        <caret>
    """.trimIndent())

    assertThat(variantsToText(variants)).isEqualTo("""
    configurations (array)
    """.trimIndent())
  }
  
  private fun checkDescription(variants: List<LookupElement>, name: String, expectedDescription: String) {
    val variant = variants.first { it.lookupString == name }
    val presentation = LookupElementPresentation()
    variant.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo(expectedDescription)
  }

  private fun test(@Language("YAML") text: String): List<LookupElement> {
    val position = EditorTestUtil.getCaretPosition(text)
    assertThat(position).isGreaterThan(0)

    val file = createFile(myModule, "intellij.yaml", text.replace("<caret>", "IntelliJIDEARulezzz"))
    val element = file.findElementAt(position)
    assertThat(element).isNotNull

    val schemaObject = JsonSchemaReader.readFromFile(myProject, schemaFile!!)
    assertThat(schemaObject).isNotNull

    return JsonSchemaCompletionContributor.getCompletionVariants(schemaObject, element!!, element)
  }
}

private fun variantsToText(variants: List<LookupElement>): String {
  return variants
    .asSequence()
    .sortedBy { it.lookupString }
    .joinToString("\n") { "${it.lookupString} (${getTypeTest(it)})" }
}

private fun getTypeTest(variant: LookupElement): String {
  val presentation = LookupElementPresentation()
  variant.renderElement(presentation)
  return presentation.typeText!!
}