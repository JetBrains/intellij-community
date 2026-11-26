// "Remove redundant elvis operator" "true"
fun foo() {
    val b = null <caret>?: /* keep */ "s"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessElvisFix
