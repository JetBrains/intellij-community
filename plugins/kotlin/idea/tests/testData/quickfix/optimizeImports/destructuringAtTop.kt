// "Optimize imports" "true"
// WITH_STDLIB

<caret>import kotlin.collections.*

fun test() {
    val (a, b, c) = Triple(1, 2, 3)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.KotlinOptimizeImportsQuickFix