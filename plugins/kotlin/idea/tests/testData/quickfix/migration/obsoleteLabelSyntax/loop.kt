// "Replace with label loop@" "true"

fun foo() {
    @loop<caret> for (i in 1..100) {
        break@loop
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceObsoleteLabelSyntaxFix