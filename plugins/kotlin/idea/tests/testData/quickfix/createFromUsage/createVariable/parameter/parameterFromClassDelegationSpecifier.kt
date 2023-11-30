// "Create parameter 'b'" "true"

open class A(val a: Int) {

}

class B: A(<caret>b) {

}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix