package org.jetbrains.completion.full.line.local

import org.junit.jupiter.api.Assertions.assertFalse

class MavenMetadataTest : XmlSerializationTest() {
  fun `test encode & decode MavenMetadata`() {
    val schema = MavenMetadata(
      Versioning(
        "a.b.c",
        "c.b.a",
        listOf("p", "0"),
        987654321
      )
    )
    val xmlString = schema.fromPattern()

    val models = decodeFromXml<MavenMetadata>(xmlString)
    assertEqualsWithoutIndent(schema, models)

    val raw = encodeToXml(schema)
    // Check that group and artifact are not in string and add them, because pattern contains them.
    val group = xml("<groupId>ml.intellij</groupId>")
    val art = xml("<artifactId>completion-engine</artifactId>")
    assertFalse(raw.contains(group), "Group was added by serialization")
    assertFalse(raw.contains(art), "Artifact was added by serialization")

    val fixed = encodeToXml(schema).lines().toMutableList()
      .apply { addAll(1, listOf(group, art)) }
      .joinToString("\n")
    assertEqualsWithoutIndent(xmlString, fixed)
  }

  fun `test pattern in test`() {
    val schema = MavenMetadata(
      Versioning(
        "1.2.3",
        "3.2.1",
        listOf("1a", "2b", "3c"),
        1234567890
      )
    )
    val expected = xml(
      """
            <metadata>
                <groupId>ml.intellij</groupId>
                <artifactId>completion-engine</artifactId>
                <versioning>
                    <latest>1.2.3</latest>
                    <release>3.2.1</release>
                    <versions>
                        <version>1a</version>
                        <version>2b</version>
                        <version>3c</version>
                    </versions>
                    <lastUpdated>1234567890</lastUpdated>
                </versioning>
            </metadata>
            """.trimIndent()
    )

    val xml = schema.fromPattern()
    assertEqualsWithoutIndent(expected, xml)
  }

  private fun MavenMetadata.fromPattern(): String {
    val versions = versioning.versions.joinToString("\n") {
      xml("<version>$it</version>")
    }
    return xml(
      """
            <metadata>
                <groupId>ml.intellij</groupId>
                <artifactId>completion-engine</artifactId>
                <versioning>
                    <latest>${versioning.latest}</latest>
                    <release>${versioning.release}</release>
                    <versions>
                        ${versions}
                    </versions>
                    <lastUpdated>${versioning.lastUpdated}</lastUpdated>
                </versioning>
            </metadata>
            """.trimIndent()
    )
  }
}
