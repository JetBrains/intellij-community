// "Rename to _" "true"
fun foo(block: (String, Int) -> Unit) {
    block("", 1)
}

fun bar() {
    foo { x<caret>: String, y: Int ->
        y.hashCode()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RenameToUnderscoreFix
// In K2, this is handled by org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.UnusedVariableInspection
// see https://youtrack.jetbrains.com/issue/KTIJ-29532/K2-IDE-Port-RenameToUnderscoreFix#focus=Change-27-10072644.0-0