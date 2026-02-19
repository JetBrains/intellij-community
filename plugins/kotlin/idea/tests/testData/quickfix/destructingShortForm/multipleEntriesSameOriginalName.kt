// "Convert to a full name-based destructuring form" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// IGNORE_K1
// WITH_STDLIB

fun <A, B, C> test(triple: Triple<A, B, C>) {
    val (a, b, c<caret>) = triple
    // Multiple entries mapping to different original names - should work
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertNameBasedDestructuringShortFormToFullFix