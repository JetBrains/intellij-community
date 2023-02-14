package com.intellij.configurationScript

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.configurationScript.providers.PluginsConfiguration
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
import com.intellij.util.xmlb.annotations.XCollection
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
    val variants = testRunConfigurations("""
    runConfigurations:
      java:
        <caret>
    """.trimIndent())

    checkDescription(variants, "env", "Environment variables")
    checkDescription(variants, "isAllowRunningInParallel", "Allow multiple instances")
    checkDescription(variants, "isShowConsoleOnStdErr", "Show console when a message is printed to standard error stream")
    checkDescription(variants, "isShowConsoleOnStdOut", "Show console when a message is printed to standard output stream")
  }

  fun `test array or object`() {
    val variants = testRunConfigurations("""
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
    val variants = testRunConfigurations("""
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
    testFooComponentState("foo".trimIndent(), """
      foo:
        <caret>
    """)
  }

  fun `test component state - nested key`() {
    testFooComponentState("foo.bar", """
      foo:
        bar:
          <caret>
    """)
  }

  fun `test list of objects`() {
    testComponentState("sdks", """
      sdks:
        <caret>
    """, beanClass = JdkAutoHints::class.java, expectedVariants = """sdks (array)""")
  }

  fun `test list of strings`() {
    testComponentState("plugins", """
      plugins:
        <caret>
    """, beanClass = PluginsConfiguration::class.java, expectedVariants = """repositories (array)""")
  }

  private fun testFooComponentState(path: String, fileContent: String) {
    testComponentState(path, fileContent, Foo::class.java, expectedVariants = "a (string)")
  }

  private fun testComponentState(path: String, @Language("YAML") fileContent: String, beanClass: Class<out BaseState>, expectedVariants: String) {
    val variants = test(fileContent.trimIndent(), listOf(object : SchemaGenerator {
      private val componentStateJsonSchemaGenerator = ComponentStateJsonSchemaGenerator()

      override fun generate(rootBuilder: JsonObjectBuilder) {
        componentStateJsonSchemaGenerator.doGenerate(rootBuilder, mapOf(path to beanClass))
      }

      override val definitionNodeKey: CharSequence
        get() = componentStateJsonSchemaGenerator.definitionNodeKey

      override fun generateDefinitions() = componentStateJsonSchemaGenerator.generateDefinitions()
    }), schemaValidator = {
      val snapshotFile = testSnapshotDir.resolve(sanitizeFileName(name) + ".json")
      assertThat(it.toString()).toMatchSnapshot(snapshotFile)
    })

    assertThat(variantsToText(variants)).isEqualTo(expectedVariants)
  }

  private fun checkDescription(variants: List<LookupElement>, name: String, expectedDescription: String) {
    val variant = variants.first { it.lookupString == name }
    val presentation = LookupElementPresentation()
    variant.renderElement(presentation)
    assertThat(presentation.typeText).isEqualTo(expectedDescription)
  }

  private fun testRunConfigurations(@Language("YAML") text: String, schemaValidator: ((CharSequence) -> Unit)? = null): List<LookupElement> {
    return test(text, listOf(RunConfigurationJsonSchemaGenerator()), schemaValidator)
  }

  private fun test(@Language("YAML") text: String, generators: List<SchemaGenerator>, schemaValidator: ((CharSequence) -> Unit)? = null): List<LookupElement> {
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

@Suppress("unused")
internal class JdkAutoHint: BaseState() {
  var sdkName by string()
  var sdkPath by string()
}

internal class JdkAutoHints : BaseState() {
  @get:XCollection
  val sdks by list<JdkAutoHint>()
}