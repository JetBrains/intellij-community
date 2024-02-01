// "Create local variable 'foo'" "true"

package foo

fun test(): Int {
    return <caret>foo
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateLocalVariableActionFactory$CreateLocalFromUsageAction