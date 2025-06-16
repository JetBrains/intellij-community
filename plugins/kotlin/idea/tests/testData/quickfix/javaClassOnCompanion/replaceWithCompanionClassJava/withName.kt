// "Replace with 'Companion::class.java'" "true"
// WITH_STDLIB
fun main() {
    val name = Int.javaClass<caret>.name
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithCompanionClassJavaFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithCompanionClassJavaFix