// "Create local variable 'foo'" "true"

fun test(n: Int) {
    val i = 1
    test(i)
    test(i + 1)
    test(<caret>foo)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateLocalVariableActionFactory$CreateLocalFromUsageAction