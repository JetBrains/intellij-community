object A {
    const val a = """blah<caret> blah"""
}

// COMPILER_ARGUMENTS: -XXLanguage:+IntrinsicConstEvaluation
// IGNORE_FORMATTER