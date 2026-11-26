// "Remove redundant elvis operator" "true"
fun foo() {
    val b = null /* first */ <caret>?: /* second */ "s"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessElvisFix
