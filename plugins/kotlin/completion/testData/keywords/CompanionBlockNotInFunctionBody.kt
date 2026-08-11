// FIR_IDENTICAL
// FIR_COMPARISON
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks

fun foo() {
    <caret>
}

// ABSENT: companion
// ABSENT: companion object
