// "Create parameter 's'" "true"

fun foo(n: Int) {

}

fun bar() {
    foo(n = 1, <caret>s = "2")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix