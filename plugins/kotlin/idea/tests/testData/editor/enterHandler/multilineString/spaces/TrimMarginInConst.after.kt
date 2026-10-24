object A {
    const val a = """blah
        | <caret>blah""".trimMargin()
}

// COMPILER_ARGUMENTS: -XXLanguage:+IntrinsicConstEvaluation
// IGNORE_FORMATTER