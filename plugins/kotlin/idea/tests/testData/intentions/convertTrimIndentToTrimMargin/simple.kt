// AFTER-WARNING: Variable 'x' is never used
// INTENTION_TEXT: "Convert to 'trimMargin()' call"
// WITH_STDLIB
fun test() {
    val x =
        """
                a
                b
            """.<caret>trimIndent()
}