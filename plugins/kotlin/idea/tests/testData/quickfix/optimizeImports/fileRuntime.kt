// "Optimize imports" "true"
// WITH_STDLIB
// IGNORE_K2

<caret>import java.io.*
import java.util.*

fun foo(list: ArrayList<String>) {
    list.add("")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.KotlinOptimizeImportsQuickFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.KotlinOptimizeImportsQuickFix