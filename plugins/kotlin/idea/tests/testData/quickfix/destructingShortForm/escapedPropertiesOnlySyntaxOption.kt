// "Convert to a full name-based destructuring form" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

data class Keywords(val `in`: Int, val `fun`: Int)

fun c2(k: Keywords): Int {
    val (<caret>a, b) = k
    return a + b
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertNameBasedDestructuringShortFormToFullFix
