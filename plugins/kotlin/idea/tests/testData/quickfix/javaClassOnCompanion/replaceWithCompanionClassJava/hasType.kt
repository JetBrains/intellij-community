// "Replace with 'Companion::class.java'" "true"
// WITH_STDLIB
fun main() {
    val c: Class<Int.Companion> = Int.javaClass<caret>
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceWithCompanionClassJavaFix