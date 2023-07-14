// "Create enum constant 'D'" "true"
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