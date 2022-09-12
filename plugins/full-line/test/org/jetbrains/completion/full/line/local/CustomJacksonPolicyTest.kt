package org.jetbrains.completion.full.line.local

class CustomJacksonPolicyTest : XmlSerializationTest() {
  fun `test ensure support versionless schema`() {
    val xmlString = xml(
      """
            <models/>
            """.trimIndent()
    )

    val schema = decodeFromXml<LocalModelsSchema>(xmlString)
    assertEqualsWithoutIndent(LocalModelsSchema(null, mutableListOf()), schema)
  }

  fun `test encode & decode LocalModelsSchema`() {
    val pyModel = ModelSchema(
      "1.2.3",
      1234567890,
      "python",
      listOf("python", "kt"),
      BinarySchema("flcc.model"),
      BPESchema("flcc.bpe"),
      ConfigSchema("flcc.json"),
      "Lorem ipsum"
    )
    val javaModel = ModelSchema(
      "3.2.1",
      987654321,
      "java",
      listOf("java", "kotlin"),
      BinarySchema("flcc.model"),
      BPESchema("flcc.bpe"),
      ConfigSchema("flcc.json"),
      "Lorem ipsum!"
    )

    val xmlString = xml(
      """
            <models version="1">
                ${pyModel.fromPattern()}
                ${javaModel.fromPattern()}
            </models>
            """.trimIndent()
    )

    val models = decodeFromXml<LocalModelsSchema>(xmlString)
    assertEqualsWithoutIndent(LocalModelsSchema(1, mutableListOf(pyModel, javaModel)), models)

    val raw = encodeToXml(LocalModelsSchema(1, mutableListOf(pyModel, javaModel)))
    assertEqualsWithoutIndent(xmlString, raw)
  }

  fun `test pattern in test`() {
    val schema = ModelSchema(
      "snapshot",
      123,
      "java",
      listOf("java", "py", "kt"),
      BinarySchema("p1"),
      BPESchema("p2"),
      ConfigSchema("p3"),
      "changes"
    )
    val expected = xml(
      """
                <model currentLanguage="java">
                    <version>snapshot</version>
                    <size>123</size>
                    <languages>
                        <language>java</language>
                        <language>py</language>
                        <language>kt</language>
                    </languages>
                    <binary>p1</binary>
                    <bpe>p2</bpe>
                    <config>p3</config>
                    <changelog>changes</changelog>
                </model>
            """.trimIndent()
    )

    val xml = schema.fromPattern()
    assertEqualsWithoutIndent(expected, xml)
  }

  private fun ModelSchema.fromPattern(): String {
    val languages = languages.joinToString("\n") {
      xml("<language>$it</language>")
    }
    return xml(
      """
                <model currentLanguage="${currentLanguage}">
                    <version>${version}</version>
                    <size>${size}</size>
                    <languages>
                        $languages
                    </languages>
                    <binary>${binary.path}</binary>
                    <bpe>${bpe.path}</bpe>
                    <config>${config.path}</config>
                    <changelog>${changelog}</changelog>
                </model>
            """.trimIndent()
    )
  }
}
