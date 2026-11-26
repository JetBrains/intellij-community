// "Remove redundant elvis operator" "true"
fun foo() {
    val b = null /* keep */ <caret>?: "s"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessElvisFix
