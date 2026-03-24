// "Create enum constant 'D'" "true"
// K2_ERROR: Unresolved reference 'D'.
enum class Test {
    A,
    B,
    C;

    fun test() {
    }
}

fun main() {
    Test.D<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction