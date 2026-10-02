// "Convert to positional destructuring syntax with square brackets" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
// WITH_STDLIB

fun <K, V> putAll(from: Map<out K, V>) {
    for ((<caret>key, value) in from) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConvertToPositionalDestructuringFix