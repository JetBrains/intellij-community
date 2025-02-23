// "Create class 'AAA'" "true"
// KEEP_ACTIONS_LIST_ORDER
// K2_ACTIONS_LIST: Introduce local variable
// K2_ACTIONS_LIST: Add braces to 'when' entry
// K2_ACTIONS_LIST: Add braces to all 'when' entries
// K2_ACTIONS_LIST: Create annotation 'AAA'
// K2_ACTIONS_LIST: Create class 'AAA'
// K2_ACTIONS_LIST: Create enum 'AAA'
// K2_ACTIONS_LIST: Create interface 'AAA'
// K2_ACTIONS_LIST: Remove braces from 'if' statement
// K2_ACTIONS_LIST: Create function 'AAA'
// K2_ACTIONS_LIST: Enable a trailing comma by default in the formatter
// K2_ACTIONS_LIST: Replace 'when' with 'if'
// ERROR: Unresolved reference: BBB
// K2_AFTER_ERROR: Return type mismatch: expected 'I?', actual 'AAA?'.
// K2_AFTER_ERROR: Unresolved reference 'BBB'.

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
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.CreateKotlinClassAction