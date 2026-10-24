object O {
    const val s = $$"""<caret>"""
}

// COMPILER_ARGUMENTS: -XXLanguage:-IntrinsicConstEvaluation
// IGNORE_FORMATTER