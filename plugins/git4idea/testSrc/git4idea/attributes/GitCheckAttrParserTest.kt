package git4idea.attributes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    assertTrue("Missing path", parse.attributes.containsKey(path))
    val attrs = parse.attributes[path]!!
    assertTrue("CRLF attribute not found", attrs.contains(GitAttribute.CRLF))
    assertEquals("Too many attributes", 1, attrs.size)
  }
}
