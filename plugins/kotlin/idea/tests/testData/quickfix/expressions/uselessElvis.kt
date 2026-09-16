// "Remove redundant elvis operator" "true"
fun foo(a: String) {
    val b : String = a <caret>?: "s"
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessElvisFix