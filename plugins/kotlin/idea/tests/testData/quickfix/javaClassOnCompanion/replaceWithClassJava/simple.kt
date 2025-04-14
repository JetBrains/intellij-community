// "Replace with '::class.java'" "true"
// WITH_STDLIB
// PRIORITY: LOW
fun main() {
    val c = Int.javaClass<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithClassJavaFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithClassJavaFix