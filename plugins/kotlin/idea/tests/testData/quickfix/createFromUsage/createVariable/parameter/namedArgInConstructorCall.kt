// "Create parameter 's'" "true"

class Foo(val n: Int) {

}

fun bar() {
    Foo(n = 1, <caret>s = "2")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix