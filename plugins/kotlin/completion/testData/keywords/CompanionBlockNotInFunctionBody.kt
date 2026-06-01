// FIR_IDENTICAL
// FIR_COMPARISON
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocksAndExtensions

fun foo() {
    <caret>
}

// ABSENT: companion
// ABSENT: companion object
