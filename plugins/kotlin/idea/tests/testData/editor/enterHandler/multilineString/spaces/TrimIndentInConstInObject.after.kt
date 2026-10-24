object O {
    const val s = """
        <caret>
    """.trimIndent()
}

// COMPILER_ARGUMENTS: -XXLanguage:+IntrinsicConstEvaluation
// IGNORE_FORMATTER