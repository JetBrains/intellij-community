// "Create local variable 'foo'" "true"

fun test(n: Int): Int {
    return when (n) {
        1 -> {
            <caret>foo
        }
        else -> {
            n + 1
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateLocalVariableActionFactory$CreateLocalFromUsageAction