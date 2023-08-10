// "Remove useless elvis operator" "true"
fun foo(a: String) {
    val b : String = (((a <caret>?: "s")))
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessElvisFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessElvisFix