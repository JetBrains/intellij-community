@Suppress("unused")
fun redundantSuppression() = Unit

@Suppress("unused")
fun suppressed() = Unit

fun <warning descr="Function \"unusedFunction\" is never used">unusedFunction</warning>() {
    redundantSuppression()
}

// NO_CHECK_INFOS
// TOOL: com.intellij.codeInspection.RedundantSuppressInspection
// TOOL: org.jetbrains.kotlin.idea.inspections.UnusedSymbolInspection
// TOOL: org.jetbrains.kotlin.idea.inspections.UnusedReceiverParameterInspection
