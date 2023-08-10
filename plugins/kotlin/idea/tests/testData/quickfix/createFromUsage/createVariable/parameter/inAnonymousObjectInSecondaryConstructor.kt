// "Create parameter 'name'" "true"
class B {
    constructor() {
        object : A(<caret>name) {

        }
    }
}

open class A(s: String)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix