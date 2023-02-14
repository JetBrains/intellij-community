// WITH_STDLIB
fun test(s: String, s2: String): String {
    val f = <caret>"""
      ${s.length}+
      ${s2.length}
    """.trimIndent()
    return f
}
