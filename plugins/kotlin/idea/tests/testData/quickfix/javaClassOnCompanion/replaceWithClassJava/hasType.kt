// "Replace with '::class.java'" "true"
// WITH_STDLIB
// DISABLE_ERRORS
fun main() {
    val c: Class<Int.Companion> = Int.javaClass<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithClassJavaFix