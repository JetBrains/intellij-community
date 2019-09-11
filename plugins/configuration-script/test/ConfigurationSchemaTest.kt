package com.intellij.configurationScript

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.configurationScript.schemaGenerators.ComponentStateJsonSchemaGenerator
import com.intellij.configurationScript.schemaGenerators.RunConfigurationJsonSchemaGenerator
import com.intellij.json.JsonFileType
import com.intellij.openapi.components.BaseState
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil.getCommunityPath
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.io.sanitizeFileName
import com.jetbrains.jsonSchema.impl.JsonSchemaCompletionContributor
import com.jetbrains.jsonSchema.impl.JsonSchemaReader
import org.intellij.lang.annotations.Language
import org.jetbrains.io.JsonObjectBuilder
import java.nio.file.Paths

// this test requires YamlJsonSchemaCompletionContributor, that's why intellij.yaml is added as test dependency
internal class ConfigurationSchemaTest : BasePlatformTestCase() {
  companion object {
    private val testSnapshotDir = Paths.get(getCommunityPath(), "plugins/configuration-script", "testSnapshots")
  }

  fun `test map and description`() {
    val variants = test("""
    runConfigurations:
      java:
        <caret>
    """.trimIndent())

    checkDescription(variants, "env", "Environment variables")
    checkDescription(variants, "isAllowRunningInParallel", "Allow parallel run")
    checkDescription(variants, "isShowConsoleOnStdErr", "Show console when a message is printed to standard error stream")
    checkDescription(variants, "isShowConsoleOnStdOut", "Show console when a message is printed to standard output stream")
  }

  fun `test array or object`() {
    val variants = test("""
    runConfigurations:
      java: <caret>
    """.trimIndent())

    val texts = variants.map {
      val presentation = LookupElementPresentation()
      it.renderElement(presentation)
      presentation.itemText
    }
    assertThat(texts).contains("{...}", "[...]")
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

  private class Foo : BaseState() {
    @Suppress("unused")
    var a by string()
  }

  fun `test component state`() {
    doTestComponentState("foo".trimIndent(), """
      foo:
        <caret>
    """)
  }

  fun `test component state - nested key`() {
    doTestComponentState("foo.bar", """
      foo:
        bar:
          <caret>
    """)
  }

  private fun doTestComponentState(path: String, fileContent: String) {
    val variants = test(fileContent.trimIndent(), listOf(object : SchemaGenerator {
      override fun generate(rootBuilder: JsonObjectBuilder) {
        val pathToStateClass = mapOf(path to Foo::class.java)
        val schemaGenerator = ComponentStateJsonSchemaGenerator()
        schemaGenerator.doGenerate(rootBuilder, pathToStateClass)
      }
    }), schemaValidator = {
      val snapshotFile = testSnapshotDir.resolve(sanitizeFileName(name) + ".json")
      assertThat(it.toString()).toMatchSnapshot(snapshotFile)
    })

    assertThat(variantsToText(variants)).isEqualTo("""
    a (string)
    """.trimIndent())
  }

  private fun checkDescription(variants: List<LookupElement>, name: String, expectedDescription: String) {
    val variant = variants.first { it.lookupString == name }
    val presentation = LookupElementPresentation()
    variant.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo(expectedDescription)
  }

  private fun test(@Language("YAML") text: String, generators: List<SchemaGenerator> = listOf(RunConfigurationJsonSchemaGenerator()), schemaValidator: ((CharSequence) -> Unit)? = null): List<LookupElement> {
    val position = EditorTestUtil.getCaretPosition(text)
    assertThat(position).isGreaterThan(0)

    @Suppress("SpellCheckingInspection")
    val file = myFixture.configureByText("intellij.yaml", text.replace("<caret>", "<caret>IntelliJIDEARulezzz"))
    val element = file.findElementAt(myFixture.editor.caretModel.offset)
    assertThat(element).isNotNull

    val schemaContent = doGenerateConfigurationSchema(generators)
    schemaValidator?.invoke(schemaContent)
    val schemaFile = LightVirtualFile("scheme.json", JsonFileType.INSTANCE, schemaContent, Charsets.UTF_8, 0)

    val schemaObject = JsonSchemaReader.readFromFile(project, schemaFile)
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