// "Rename variables to match destructuring property names" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
// WITH_STDLIB

fun <K, V> putAll(from: Map<out K, V>) {
    for ((val<caret>ue, key) in from) {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.DestructuringRenameFactory$RenameDestructuringEntriesToMatchPropertiesFix