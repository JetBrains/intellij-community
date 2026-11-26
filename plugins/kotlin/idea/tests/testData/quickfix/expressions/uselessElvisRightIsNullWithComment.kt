// "Remove redundant elvis operator" "true"
fun foo(a: String?) {
    val b = a <caret>?: /* keep */ null
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessElvisFix
