// "Create parameter 'name'" "true"
fun f() {
    object : A(<caret>name) {

    }
}

open class A(s: String)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix