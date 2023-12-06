// "Create local variable 'foo'" "true"

fun test(n: Int) {
    <caret>foo = n + 1
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateLocalVariableActionFactory$CreateLocalFromUsageAction