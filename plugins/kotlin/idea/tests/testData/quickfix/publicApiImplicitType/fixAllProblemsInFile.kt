// "Fix all 'Public API declaration with implicit return type' problems in file" "true"

interface A {
    fun foo<caret>() = java.util.Optional.of(42)
    fun bar() = java.util.Optional.of(42)
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInspection.actions.CleanupInspectionIntention