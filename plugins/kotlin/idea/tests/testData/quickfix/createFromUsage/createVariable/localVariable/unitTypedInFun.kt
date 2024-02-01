// "Create local variable 'foo'" "true"

fun test() {
    val u: Unit = <caret>foo
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateLocalVariableActionFactory$CreateLocalFromUsageAction