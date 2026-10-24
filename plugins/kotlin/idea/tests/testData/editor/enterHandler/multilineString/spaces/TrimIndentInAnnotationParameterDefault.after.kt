annotation class Ann(val s: String = """
    <caret>
""".trimIndent())

// COMPILER_ARGUMENTS: -XXLanguage:+IntrinsicConstEvaluation
// IGNORE_FORMATTER