package git4idea.attributes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitCheckAttrParserTest {

  @Test
  fun test() {
    val output = """

      org/example/MyClass.java: crlf: unset
      org/example/MyClass.java: diff: java
      org/example/MyClass.java: myAttr: set
      """.trimIndent()
    val parse = GitCheckAttrParser.parse(output.lines())
    val path = "org/example/MyClass.java"
    assertTrue(parse.attributes.containsKey(path), "Missing path")
    val attrs = parse.attributes[path]!!
    assertTrue(attrs.contains(GitAttribute.CRLF), "CRLF attribute not found")
    assertEquals(1, attrs.size, "Too many attributes")
  }
}
