// "Create class 'AAA'" "true"
// ERROR: Unresolved reference: BBB

abstract class I

fun test(n: Int): I? {
    return if (n > 0) {
        when (n) {
            1 -> <caret>AAA("1")
            2 -> BBB("2")
            else -> null
        }
    }
    else null
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createClass.CreateClassFromUsageFix