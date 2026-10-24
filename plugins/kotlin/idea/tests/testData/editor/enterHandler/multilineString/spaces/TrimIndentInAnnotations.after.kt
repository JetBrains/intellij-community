@Annotation("""
    <caret>
""".trimIndent())
fun some() {}

// COMPILER_ARGUMENTS: -XXLanguage:+IntrinsicConstEvaluation
// IGNORE_FORMATTER