// "Remove redundant elvis operator" "true"
fun foo(a: String) {
    val b: String = a <caret>?: /* keep */ "s"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessElvisFix
