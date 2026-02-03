// "Change parameter 'param' type of function 'handle' to 'Callback'" "true"
interface Callback {
    fun onEvent()
}

fun handle(param: Int) {}

fun test() {
    handle(<caret>object : Callback {
        override fun onEvent() {}
    })
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeParameterTypeFix
